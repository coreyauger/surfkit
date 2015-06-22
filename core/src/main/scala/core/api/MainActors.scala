package io.surfkit.core.api

import akka.actor.Props
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress

trait MainActors {
  this : AbstractSystem =>

  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  // TODO: create ANY type of echange here..
  lazy val props = RabbitDispatcher.props(RabbitMqAddress(config.getString("rabbitmq.host"), config.getInt("rabbitmq.port")))

  lazy val v1 = system.actorOf(Props(new io.surfkit.core.service.v1.ServiceActor(props)), "v1")

}
