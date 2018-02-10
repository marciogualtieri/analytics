package dal.caching

import java.sql.Timestamp

import akka.actor.ActorSystem
import dal.SlickEventRepository
import helpers.TestHelper
import models.{Event, EventKind}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import utils.time._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class CachedEventRepositorySpec extends PlaySpec with BeforeAndAfter with MockitoSugar with TestHelper {

  val mockRepo: SlickEventRepository = mock[SlickEventRepository]
  val mockClock: Clock = mock[Clock]
  val actorSystem = ActorSystem("TestActorSystem")

  setupAllMocksForTestScenario()

  val cachedRepo = new CachedEventRepository(mockRepo, mockClock, actorSystem)

  "Caching" should {

    "load cached data on creation" in {
      val counts = Await.result(cachedRepo.counts(CurrentHour), Duration.Inf)
      counts mustBe(3, 2, 1)
      verify(mockRepo, times(1)).counts(CurrentHour)
    }

    "return cached data for current hour" in {
      setupAllMocksForTestScenario()
      val counts = Await.result(cachedRepo.counts(CurrentHour), Duration.Inf)
      counts mustBe(3, 2, 1)
      verify(mockRepo, never()).counts(CurrentHour)
    }

    "return repository data for past hour" in {
      setupAllMocksForTestScenario()
      val counts = Await.result(cachedRepo.counts(PastHour), Duration.Inf)
      counts mustBe(1, 1, 0)
      verify(mockRepo, times(1)).counts(PastHour)
    }

    "update cached data with new events" in {
      setupAllMocksForTestScenario()
      createOneClickAndOneImpressionFromCurlyJoeInTheCurrentHourUsingTheCache
      val counts = Await.result(cachedRepo.counts(CurrentHour), Duration.Inf)
      counts mustBe(4, 3, 2)
      verify(mockRepo, never()).counts(CurrentHour)
      verify(mockRepo, times(1)).create(CurlyJoeTestUser, EventKind.Click, CurrentHourMillisecondsSinceEpoch)
    }

    "reload cached data when clock advances to next hour" in {
      setupAllMocksForTestScenario()
      setCurrentClockTo(NextHour)
      val counts = Await.result(cachedRepo.counts(NextHour), Duration.Inf)
      counts mustBe(1, 0, 1)
      verify(mockRepo, times(1)).counts(NextHour)
    }
  }

  private def createOneClickAndOneImpressionFromCurlyJoeInTheCurrentHourUsingTheCache = {
    Await.result(cachedRepo.create(CurlyJoeTestUser, EventKind.Click, CurrentHourMillisecondsSinceEpoch), Duration.Inf)
    Await.result(cachedRepo.create(CurlyJoeTestUser, EventKind.Impression, CurrentHourMillisecondsSinceEpoch), Duration.Inf)
  }

  private def setupAllMocksForTestScenario(): Unit = {
    resetAllMocksIncludingVerifyCounts()

    when(mockRepo.create(any(), any(), any()))
      .thenReturn(Future.successful(Event(0L, CurlyJoeTestUser, EventKind.Click, CurrentHourMillisecondsSinceEpoch)))

    shempGeneratesOneClickInThePastHour
    MoeCurlyAndLarryGenerateTwoClicksAndOneImpressionInTheCurrentHour
    JoeGeneratesOneImpressionInTheNextHour

    setCurrentClockTo(CurrentHour)
  }

  private def resetAllMocksIncludingVerifyCounts(): Unit = {
    reset(mockRepo)
    reset(mockClock)
  }

  private def shempGeneratesOneClickInThePastHour = {
    when(mockRepo.counts(PastHour)).thenReturn(Future.successful((1, 1, 0)))
    when(mockRepo.distinctUsers(PastHour)).thenReturn(Future.successful(Seq(ShempTestUser)))
  }
  private def MoeCurlyAndLarryGenerateTwoClicksAndOneImpressionInTheCurrentHour = {
    when(mockRepo.counts(CurrentHour)).thenReturn(Future.successful((3, 2, 1)))
    when(mockRepo.distinctUsers(CurrentHour)).thenReturn(Future.successful(Seq(MoeTestUser, CurlyTestUser, LarryTestUser)))
  }

  private def JoeGeneratesOneImpressionInTheNextHour = {
    when(mockRepo.counts(3L)).thenReturn(Future.successful((1, 0, 1)))
    when(mockRepo.distinctUsers(3L)).thenReturn(Future.successful(Seq(JoeTestUser)))
  }

  private def setCurrentClockTo(hour: Long) = {
    when(mockClock.now()).thenReturn(new Timestamp(hour * MillisecondsInOneHour))
    when(mockClock.currentHoursSinceEpoch()).thenReturn(hour)
  }
}

