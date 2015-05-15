package io.surfkit.core.rabbitmq

import io.surfkit.model._

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{QueueingConsumer, AMQP, Channel}
import play.api.libs.json._

sealed trait RabbitMessage

object RabbitPublisher {
  
  case class RabbitUserMessage(receiverUuid: String, provider: String, msg: JsValue) extends RabbitMessage
  case class RabbitSystemMessage(appId:String, corrId: String, msg: Api.Request) extends RabbitMessage

  def props(channel: Channel, replyQueueName: String): Props = Props(new RabbitPublisher(channel, replyQueueName))
}

class RabbitPublisher(channel: Channel, replyQueueName: String) extends Actor with ActorLogging {

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

    case RabbitSystemMessage(appId, corrId, msg) =>
      //val routingKey = s"${RabbitConfig.sysExchange}.$appId"
      val headers = Map("aid" -> appId)
      val props = new AMQP.BasicProperties
      .Builder()
        .correlationId(corrId)
        .replyTo(replyQueueName)
        .build()
      // TODO: attach reply queue name to json routing

      log.debug(s"RabbitSystemMessage($appId, $msg) corrId: $corrId  reply -> $replyQueueName")
      channel.basicPublish(
        RabbitConfig.sysExchange,
        RabbitConfig.sysExchange,  // no routing key
        //new AMQP.BasicProperties.Builder().headers(headers).build(),
        props,
        upickle.write(msg).getBytes()
      )
  }
}