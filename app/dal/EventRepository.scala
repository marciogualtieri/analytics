package dal

import java.sql.Timestamp

import models.Event
import models.EventKind.EventKind

import scala.concurrent.Future

trait EventRepository {

  def create(user: String, kind: EventKind, millisecondsSinceEpoch: Timestamp): Future[Event]
  def all(): Future[Seq[Event]]
  def deleteAll(): Future[Int]
  def counts(hoursSinceEpoch: Long): Future[(Int, Int, Int)]
  def distinctUsers(hoursSinceEpoch: Long): Future[Seq[String]]

}
