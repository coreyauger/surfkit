package io.surfkit.core.api


import akka.http.server.directives.LogEntry
import io.surfkit.core.Configuration
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.core.service.v1.SurfKitService
import io.surfkit.core.socket.SocketService
import io.surfkit.core.websocket.WebSocketServer
import akka.actor.{ ActorSystem, Props }
import akka.event.Logging.InfoLevel
import scala.reflect.ClassTag
import spray.http.{ HttpRequest, StatusCodes }
import spray.routing.{ Directives, RouteConcatenation }
import spray.routing.directives.LogEntry

trait AbstractSystem {
  implicit def system : ActorSystem
}

trait SurfKitApi extends RouteConcatenation with StaticRoute with AbstractSystem {
  this : MainActors =>
  private def showReq(req : HttpRequest) = spray.routing.directives.LogEntry(req.uri, InfoLevel)

  val rootService = system.actorOf(Props(new RootService[BasicRouteActor](routes)), "routes")
  lazy val routes = logRequest(showReq _) {
      new SurfKitService(v1).route ~
      staticRoute
  }

  val wsService = system.actorOf(Props(new RootService[WebSocketServer](wsroutes)), "wss")
  lazy val wsroutes = logRequest(showReq _) {
      new SurfKitService(v1).wsroute ~
      complete(StatusCodes.NotFound)
  }
  val socketService = system.actorOf(Props[SocketService], "tcp")
}


trait StaticRoute extends Directives {
  this : AbstractSystem =>

  lazy val staticRoute =
      path("favicon.ico") {
        getFromResource("favicon.ico")
      } ~
      pathPrefix("markers") {
        getFromResourceDirectory("markers/")
      } ~
      pathPrefix("css") {
        getFromResourceDirectory("css/")
      } ~
      pathEndOrSingleSlash {
        getFromResource("index.html")
      } ~ complete(StatusCodes.NotFound)
}
