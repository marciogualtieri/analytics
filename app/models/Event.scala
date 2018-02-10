package models

import java.sql.Timestamp

object EventKind extends Enumeration {
  type EventKind = Value
  val Click: EventKind = Value("click")
  val Impression: EventKind = Value("impression")

  def exists(name: String): Boolean = values.exists(_.toString == name)
}

import EventKind._

case class Event(id: Long,
                 user: String,
                 kind: EventKind,
                 millisecondsSinceEpoch: Timestamp)