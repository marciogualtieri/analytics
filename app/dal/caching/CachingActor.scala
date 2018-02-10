package dal.caching

import akka.actor.{Actor, Props}
import models.EventKind
import models.EventKind.EventKind

import scala.collection.mutable

object CachingActor {

  def props: Props = Props[CachingActor]

  case class UpdateCache(users: Seq[String], clicks: Int, impressions: Int, hoursSinceEpoch: Long)
  case class GetCacheCounts()
  case class GetCacheHour()
  case class AddEventToCache(user: String, kind: EventKind)
}

class CachingActor extends Actor {

  import CachingActor._

  var users: mutable.Set[String] = mutable.Set()
  var clicks: Int = 0
  var impressions: Int = 0
  var hoursSinceEpoch: Long = 0L

  def receive: PartialFunction[Any, Unit] = {
    case AddEventToCache(user: String, kind: EventKind) =>
      users.add(user)
      if(kind == EventKind.Click) clicks += 1
      else impressions += 1
    case UpdateCache(_users: Seq[String], _clicks: Int, _impressions: Int, _hoursSinceEpoch: Long) =>
      users = mutable.Set(_users:_*)
      clicks = _clicks
      impressions = _impressions
      hoursSinceEpoch = _hoursSinceEpoch
    case GetCacheCounts() =>
      sender() ! (users.size, clicks, impressions)
    case GetCacheHour() =>
      sender() ! hoursSinceEpoch
  }
}