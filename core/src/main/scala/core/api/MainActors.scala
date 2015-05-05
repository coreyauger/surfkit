package io.surfkit.core.api

import akka.actor.Props

trait MainActors {
  this : AbstractSystem =>

  lazy val v1 = system.actorOf(Props[io.surfkit.core.service.v1.RabbitMqActor], "v1")

}
