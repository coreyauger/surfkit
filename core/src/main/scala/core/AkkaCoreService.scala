package io.surfkit.core

import java.net.InetSocketAddress

import akka.actor.{PoisonPill, ActorSystem}
import akka.event.{LoggingAdapter, Logging}
import akka.io.{Tcp, IO}
//import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
//import akka.stream.scaladsl.{Flow, Sink, Source}
import io.surfkit.core.api.{MainActors, SurfKitApi}
import spray.can.server.UHttp
import spray.can.Http

object AkkaCoreService extends App with MainActors with SurfKitApi {
  implicit lazy val system = ActorSystem("surfkit")
  println(s"System: $system")

  val logger = Logging(system, getClass)
  sys.addShutdownHook({ system.shutdown })
  IO(UHttp) ! Http.Bind(wsService, Configuration.host, Configuration.portWs)
  // Since the UHttp extension extends from Http extension, it starts an actor whose name will later collide with the Http extension.
  system.actorSelection("/user/IO-HTTP") ! PoisonPill
  IO(Tcp) ! Tcp.Bind(socketService, new InetSocketAddress(Configuration.host, Configuration.portTcp))
  // We could use IO(UHttp) here instead of killing the "/user/IO-HTTP" actor
  IO(Http) ! Http.Bind(rootService, Configuration.host, Configuration.portHttp)

}


object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  lazy val host = config.getString("surfkit.host")
  lazy val portHttp = config.getInt("surfkit.ports.http")
  lazy val portTcp = config.getInt("surfkit.ports.tcp")
  lazy val portWs = config.getInt("surfkit.ports.ws")

  lazy val hostRabbit = config.getString("rabbitmq.host")
  lazy val portRabbit = config.getInt("rabbitmq.port")
}