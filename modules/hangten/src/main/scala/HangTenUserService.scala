package io.surfkit.modules

import java.net.InetSocketAddress

import akka.actor.{PoisonPill, ActorSystem}
import akka.event.{LoggingAdapter, Logging}
import akka.io.{Tcp, IO}
//import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
//import akka.stream.scaladsl.{Flow, Sink, Source}
import io.surfkit.core.api.{MainActors, SurfKitApi}
import spray.can.server.UHttp
import spray.can.Http

object HangTenUserService extends App {
  implicit lazy val system = ActorSystem("hangten")
  println(s"System: $system")

  val logger = Logging(system, getClass)

}


object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  lazy val hostRabbit = config.getString("rabbitmq.host")
  lazy val portRabbit = config.getInt("rabbitmq.port")
}