package io.surfkit.modules

import akka.actor.{PoisonPill, ActorSystem}
import akka.event.{LoggingAdapter, Logging}
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.core.rabbitmq.{RabbitDispatcher, RabbitModuleConsumer}


object HangTenUserService extends App {
  implicit lazy val system = ActorSystem("hangten")
  println(s"System: $system")

  val logger = Logging(system, getClass)

  val rabbitDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  rabbitDispatcher ! RabbitDispatcher.ConnectModule  // connect to the MQ
}


object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  lazy val hostRabbit = config.getString("rabbitmq.host")
  lazy val portRabbit = config.getInt("rabbitmq.port")
}