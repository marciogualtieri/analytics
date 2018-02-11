package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import models._
import helpers.TestHelper

import org.scalatest.BeforeAndAfter

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AnalyticsControllerSpec extends PlaySpec with GuiceOneAppPerTest with BeforeAndAfter with TestHelper {

  before {
    cleanupRepository()
  }

  "AnalyticsController POST" should {

    "persist an event into the repository" in {
      val request = FakeRequest(POST, controllers.routes.AnalyticsController.createEvent().url)
        .withFormUrlEncodedBody(MoeTestClickBodyParameters: _*)
        .withHeaders(TestPostHeaders: _*)

      val response = route(app, request).get
      status(response) mustBe NO_CONTENT

      val persistedEvents = Await.result(repo.all(), Duration.Inf)
      persistedEvents mustBe Seq(Event(1, MoeTestUser, EventKind.Click, TestMillisecondsSinceEpoch))
    }

    "fail request with form parameter missing value" in {
      val request = FakeRequest(POST, controllers.routes.AnalyticsController.createEvent().url)
        .withFormUrlEncodedBody(MissingTimestampBodyParameters: _*)
        .withHeaders(TestPostHeaders: _*)

      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "fail request with form parameter with empty value" in {
      val request = FakeRequest(POST, controllers.routes.AnalyticsController.createEvent().url)
        .withFormUrlEncodedBody(EmptyValueBodyParameters: _*)
        .withHeaders(TestPostHeaders: _*)

      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "fail request with invalid event form parameter" in {
      val request = FakeRequest(POST, controllers.routes.AnalyticsController.createEvent().url)
        .withFormUrlEncodedBody(InvalidEventBodyParameters: _*)
        .withHeaders(TestPostHeaders: _*)

      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }
  }

  "AnalyticsController GET" should {

    "retrieve event counts from repository for the hour" in {
      createTestEventsInRepository()
      val baseUrl = controllers.routes.AnalyticsController.eventCounts().url
      val request = FakeRequest(GET, s"$baseUrl?timestamp=${TestMillisecondsSinceEpoch.getTime}")

      val response = route(app, request).get
      status(response) mustBe OK
      contentAsString(response) mustBe ExpectedCounts
    }
  }
}

