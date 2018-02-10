package helpers

import java.sql.Timestamp

import dal.SlickEventRepository
import models.EventKind

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import utils.time._

trait TestHelper {

  val repo: SlickEventRepository = InjectorHelper.inject[SlickEventRepository]

  val TestMillisecondsSinceEpoch: Timestamp = Timestamp.valueOf("2015-10-21 19:28:00")

  val TestOneHourTooLateMillisecondsSinceEpoch: Timestamp = Timestamp.valueOf("2015-10-21 18:28:00")

  val MoeTestUser: String = "moe.howard"

  val MoeTestClickBodyParameters: Seq[(String, String)] = Seq(("user", MoeTestUser),
    ("event", EventKind.Click.toString),
    ("timestamp", TestMillisecondsSinceEpoch.getTime.toString))

  val CurlyTestUser: String = "curly.howard"
  val LarryTestUser: String = "larry.fine"
  val ShempTestUser: String = "shemp.howard"
  val JoeTestUser: String = "joe.besser"
  val CurlyJoeTestUser: String = "curly.joe.derita"

  val PastHour = 1L
  val CurrentHour = 2L
  val NextHour = 3L

  val CurrentHourMillisecondsSinceEpoch = new Timestamp(CurrentHour * MillisecondsInOneHour)

  val TestPostHeaders = Seq(("Content-type", "application/x-www-form-urlencoded"))

  val ExpectedCounts: String =
    s"""
       |unique_users,3
       |clicks,2
       |impressions,1
    """.stripMargin

  def createTestEventsInRepository(): Unit = {
    Await.result(repo.create(MoeTestUser, EventKind.Click, TestMillisecondsSinceEpoch), Duration.Inf)
    Await.result(repo.create(CurlyTestUser, EventKind.Click, TestMillisecondsSinceEpoch), Duration.Inf)
    Await.result(repo.create(LarryTestUser, EventKind.Impression, TestMillisecondsSinceEpoch), Duration.Inf)
    Await.result(repo.create(ShempTestUser, EventKind.Impression, TestOneHourTooLateMillisecondsSinceEpoch),
      Duration.Inf)
  }

  def cleanupRepository(): Unit = Await.result(repo.deleteAll(), Duration.Inf)
}
