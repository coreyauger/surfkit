package core.api.modules


import akka.actor.ActorRef
import com.ning.http.client.AsyncHttpClientConfig
import play.api.libs.json._
import io.surfkit.model.Api._
import io.surfkit.model.Model
import play.api.libs.ws.DefaultWSClientConfig
import play.api.libs.ws.ning.{NingWSClient, NingAsyncHttpClientConfigBuilder}

import scala.concurrent.Future

/**
 * Created by suroot on 08/05/15.
 */
trait SurfKitModule {

  val wsConfig = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
  val wsBuilder = new AsyncHttpClientConfig.Builder(wsConfig)


  def actions(r:io.surfkit.model.Api.Request): PartialFunction[io.surfkit.model.Model, Future[io.surfkit.model.Api.Result]]

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
