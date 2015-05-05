package io.surfkit.core.rabbitmq

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{AMQP, Channel}
import play.api.libs.json._

sealed trait RabbitMessage

object RabbitPublisher {
  
  case class RabbitUserMessage(receiverUuid: String, provider: String, msg: JsValue) extends RabbitMessage
  case class RabbitSystemMessage(appId:String, msg: JsValue) extends RabbitMessage

  def props(channel: Channel): Props = Props(new RabbitPublisher(channel))
}

class RabbitPublisher(channel: Channel) extends Actor with ActorLogging {

  import io.surfkit.core.rabbitmq.RabbitPublisher._
  
  channel.exchangeDeclare(RabbitConfig.userExchange, "direct", true)
  channel.exchangeDeclare(RabbitConfig.sysExchange, "direct", true)
  
  override def receive = {
    case RabbitUserMessage(userId, provider, msg) =>
      val routingKey = s"${RabbitConfig.userExchange}.$userId"
      val headers = Map("uid" -> userId, "provider" -> provider)
      log.debug(s"RabbitUserMessage($userId, $provider, $msg)")
      channel.basicPublish(
        RabbitConfig.userExchange, 
        routingKey, 
        new AMQP.BasicProperties.Builder().headers(headers).build(), 
        msg.toString().getBytes()
      )

    case RabbitSystemMessage(appId, msg) =>
      val routingKey = s"${RabbitConfig.sysExchange}.$appId"
      val headers = Map("aid" -> appId)
      log.debug(s"RabbitUserMessage($appId, $msg)")
      channel.basicPublish(
        RabbitConfig.sysExchange,
        routingKey,
        new AMQP.BasicProperties.Builder().headers(headers).build(),
        msg.toString().getBytes()
      )
  }
}