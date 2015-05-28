package io.surfkit.core.service.v1


import io.surfkit.core.Configuration
import io.surfkit.core.websocket.WebSocket
import akka.actor.{ ActorRef, ActorSystem }
import play.api.libs.json.Json
import spray.can.Http
import spray.http.StatusCodes
import spray.routing.Directives

class SurfKitService(v1 : ActorRef)(implicit system : ActorSystem) extends Directives {
  lazy val route =
    pathPrefix("v1") {
      val dir = "v1/"
      pathEndOrSingleSlash {
        getFromResource(dir + "index.html")
      } ~
        pathPrefix("ws") {
          requestUri { uri =>
            val wsUri = uri.withPort(Configuration.portWs)
            system.log.debug("redirect {} to {}", uri, wsUri)
            redirect(wsUri, StatusCodes.PermanentRedirect)
          }
        } ~
        pathPrefix("api"){
          post{
            implicit ctx =>
              println("********************************")
              v1 ! RabbitMqActor.Mq(ctx.responder, ctx.unmatchedPath, Json.parse(ctx.request.entity.asString))
          } ~
            get{
              implicit ctx =>
                println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$4")
                v1 ! RabbitMqActor.Mq(ctx.responder, ctx.unmatchedPath,Json.obj())
            }
        } ~
        getFromResourceDirectory(dir)
    }
  lazy val wsroute =
    pathPrefix("v1") {
      pathPrefix("ws") {
        implicit ctx =>
          ctx.responder ! WebSocket.Register(ctx.request, v1, true)
      }
    }
}