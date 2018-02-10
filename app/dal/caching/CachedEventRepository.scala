package dal.caching

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import dal.{EventRepository, SlickEventRepository}
import models.Event
import models.EventKind.EventKind
import utils.time.{hoursSinceEpoch, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class CachedEventRepository @Inject()(repo: SlickEventRepository,
                                      clock: Clock,
                                      system: ActorSystem)(implicit ec: ExecutionContext) extends EventRepository {

  private val ActorSystemTimeout = new Timeout(5.seconds)
  private val cachingActor: ActorRef = system.actorOf(CachingActor.props, name="caching-actor")
  private implicit val timeout: Timeout = ActorSystemTimeout

  loadCachedData()

  import CachingActor._

  override def counts(hoursSinceEpoch: Long): Future[(Int, Int, Int)] = {
    if(expiredCache) loadCachedData()
    if(equalToCurrentHour(hoursSinceEpoch)) (cachingActor ? GetCacheCounts()).mapTo[(Int, Int, Int)]
    else repo.counts(hoursSinceEpoch)
  }

  override def create(user: String, kind: EventKind, millisecondsSinceEpoch: Timestamp): Future[Event] = {
    if(expiredCache) loadCachedData()
    val eventHour: Long = hoursSinceEpoch(millisecondsSinceEpoch)
    if(equalToCurrentHour(eventHour)) cachingActor ! AddEventToCache(user, kind)
    repo.create(user, kind, millisecondsSinceEpoch)
  }

  override def all(): Future[Seq[Event]] = repo.all()

  override def deleteAll(): Future[Int] = repo.deleteAll()

  override def distinctUsers(hoursSinceEpoch: Long): Future[Seq[String]] = repo.distinctUsers(hoursSinceEpoch)

  private def equalToCurrentHour(eventHour: Long) = {
    val currentHour: Long = clock.currentHoursSinceEpoch()
    eventHour == currentHour
  }

  private def expiredCache = {
    val cachingHour: Long = Await.result((cachingActor ? GetCacheHour()).mapTo[Long], Duration.Inf)
    val currentHour: Long = clock.currentHoursSinceEpoch()
    currentHour > cachingHour
  }

  private def loadCachedData(): Unit = {
    val currentHour: Long = clock.currentHoursSinceEpoch()
    val (_, clicks, impressions) = Await.result(repo.counts(currentHour), Duration.Inf)
    val distinctUsers = Await.result(repo.distinctUsers(currentHour), Duration.Inf)
    cachingActor ! UpdateCache(distinctUsers, clicks, impressions, currentHour)
  }
}
