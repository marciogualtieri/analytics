package controllers

import javax.inject._

import models._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import java.sql.Timestamp

import dal.caching.CachedEventRepository
import entities.Counts
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.{ExecutionContext, Future}
import utils.time._

class AnalyticsController @Inject()(repo: CachedEventRepository,
                                    cc: MessagesControllerComponents)
                                   (implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  val MillisecondsSinceEpochParameterName = "timestamp"

  case class UserData(user: String, event: String, millisSinceEpoch: Long)


  val eventForm = Form {
    mapping(
      "user" -> nonEmptyText,
      "event" -> nonEmptyText,
      MillisecondsSinceEpochParameterName -> longNumber
    )(UserData.apply)(UserData.unapply) verifying("Failed form constraints!", fields => fields match {
      case userData => EventKind.exists(userData.event)
    })
  }

  def index = Action { implicit request =>
    Ok(views.html.index("It's Alive!"))
  }

  def createEvent: Action[AnyContent] = Action.async { implicit request =>
    eventForm.bindFromRequest.fold(
      _ => {
        Future.successful(BadRequest(views.html.index("Invalid Request!")))
      },
      event => {
        repo.create(event.user, EventKind.withName(event.event), new Timestamp(event.millisSinceEpoch)).map { _ =>
          NoContent
        }
      }
    )
  }


  def eventCounts: Action[AnyContent] = Action.async { implicit request =>
    val millisecondsSinceEpoch: Timestamp = millisSinceEpochQueryParameter(request)
    repo.counts(hoursSinceEpoch = hoursSinceEpoch(millisecondsSinceEpoch)).map { case (users, clicks, impressions) =>
      Ok(Counts(users, clicks, impressions).toString)
    }
  }

  private def millisSinceEpochQueryParameter(request: MessagesRequest[AnyContent]) = {
    val currentMillisecondsSinceEpoch = DateTime.now(DateTimeZone.UTC).getMillis.toString
    val millisecondsSinceEpochQuery: Option[String] = request.getQueryString(MillisecondsSinceEpochParameterName)
    val millisecondsSinceEpoch: Timestamp =
      new Timestamp(millisecondsSinceEpochQuery.getOrElse(currentMillisecondsSinceEpoch).toLong)
    millisecondsSinceEpoch
  }
}