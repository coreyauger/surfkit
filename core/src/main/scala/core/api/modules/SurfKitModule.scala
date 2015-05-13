package core.api.modules


import akka.actor.ActorRef
import play.api.libs.json._
import io.surfkit.model.Api._
import io.surfkit.model.Model

import scala.concurrent.Future

/*
object SurfKitModule {

  sealed trait Api

  case class ApiRoute(id: String, reply: String, tag: Long) extends Api

  case class ApiRequest(module: String, op: String, routing: ApiRoute, data: String) extends Api

  case class ApiResult(module: String, op: String, routing: ApiRoute, data: String) extends Api

  implicit val rr     = Json.reads[ApiRoute]
  implicit val wr    = Json.writes[ApiRoute]

  implicit val rreq     = Json.reads[ApiRequest]
  implicit val wreq    = Json.writes[ApiRequest]

  implicit val rres     = Json.reads[ApiResult]
  implicit val wres    = Json.writes[ApiResult]

}
*/
/**
 * Created by suroot on 08/05/15.
 */
trait SurfKitModule {


  def actions(r:ApiRequest): PartialFunction[io.surfkit.model.Model, Future[ApiResult]]

  /*
  def validate[T <: io.surfkit.model.Model](r: ApiRequest)(implicit reader: Reads[T]) = {

    r.data.validate[T] match {
      case JsSuccess(msg, _) => actions(r)(msg)
      case JsError(e)        =>
        // TODO: what do we want to do on error here?
        println(e)
        Future.successful(ApiResult(r.module, r.op, r.routing, Json.obj()))
      //sender ! MessageProcessError(JsError.toFlatJson(e))
    }

  }
  */

}
