package dal

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import models.EventKind.EventKind
import models.{Event, EventKind}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.time._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlickEventRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends EventRepository {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  private implicit val eventKindMapper: BaseColumnType[EventKind] =
    MappedColumnType.base[EventKind, String](e => e.toString, s => EventKind.withName(s))

  val rowToEvent: ((Long, String, EventKind, Timestamp, Long)) => Event = {
    case (id, user, kind, millisecondsSinceEpoch, _) => Event(id, user, kind, millisecondsSinceEpoch)
  }

  val eventToRow: Event => Option[(Long, String, EventKind, Timestamp, Long)] =
    e => Some((e.id, e.user, e.kind, e.millisecondsSinceEpoch, hoursSinceEpoch(e.millisecondsSinceEpoch)))

  private class EventTable(tag: Tag) extends Table[Event](tag, "event") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def user = column[String]("user")
    def kind = column[EventKind]("kind")
    def millisecondSinceEpoch = column[Timestamp]("milliseconds_from_epoch")
    def hoursSinceEpoch = column[Long]("hours_from_epoch")

    def * = (id, user, kind, millisecondSinceEpoch, hoursSinceEpoch) <> (rowToEvent, eventToRow)
  }

  private val allEvents = TableQuery[EventTable]

  override def create(user: String, kind: EventKind, millisecondsSinceEpoch: Timestamp): Future[Event] = db.run {
    (allEvents.map(e => (e.user, e.kind, e.millisecondSinceEpoch, e.hoursSinceEpoch))
      returning allEvents.map(_.id)
      into ((cols, id) => Event(id, cols._1, cols._2, cols._3))
      ) += (user, kind, millisecondsSinceEpoch, hoursSinceEpoch(millisecondsSinceEpoch))
  }

  override def all(): Future[Seq[Event]] = db.run {
    allEvents.result
  }

  override def deleteAll(): Future[Int] = db.run {
      allEvents.delete
  }

  override def counts(hoursSinceEpoch: Long): Future[(Int, Int, Int)] = db.run {
    val events = allEvents.filter { e => e.hoursSinceEpoch === hoursSinceEpoch }
    val distinctUsers = events.map { e => e.user }.distinct.length
    val clicks = events.filter { e => e.kind === EventKind.Click }.length
    val impressions = events.filter { e => e.kind === EventKind.Impression }.length
    val counts = Query(distinctUsers, clicks, impressions)
    counts.result.head
  }

  override def distinctUsers(hoursSinceEpoch: Long): Future[Seq[String]] = db.run {
    val events = allEvents.filter { e => e.hoursSinceEpoch === hoursSinceEpoch }
    val users = events.map { e => e.user }.distinct
    users.result
  }

}
