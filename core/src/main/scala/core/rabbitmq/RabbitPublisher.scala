package io.surfkit.core.rabbitmq

import io.surfkit.model._

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{QueueingConsumer, AMQP, Channel}

sealed trait RabbitMessage

object RabbitPublisher {
  
  case class RabbitUserMessage(receiverUid: Long, appId: String, msg: Api.Request) extends RabbitMessage
  case class RabbitSystemMessage(module:String, appId:String, corrId: String, msg: Api.Request) extends RabbitMessage

  def props(channel: Channel, replyQueueName: String): Props = Props(new RabbitPublisher(channel, replyQueueName))
}

class RabbitPublisher(channel: Channel, replyQueueName: String) extends Actor with ActorLogging {

  import io.surfkit.core.rabbitmq.RabbitPublisher._
  
  channel.exchangeDeclare(RabbitConfig.userExchange, "direct", true)
  channel.exchangeDeclare(RabbitConfig.sysExchange, "direct", true)

  override def receive = {
    case RabbitUserMessage(userId, appId, msg) =>
      println("RabbitUserMessage")
      val routingKey = s"${RabbitConfig.userExchange}.$userId"
      val headers = Map("uid" -> userId.toString)
      log.debug(s"RabbitUserMessage($userId, $appId, $msg)")
      channel.basicPublish(
        RabbitConfig.userExchange, 
        routingKey, 
        new AMQP.BasicProperties.Builder().headers(headers).build(),
        upickle.write(msg).getBytes()
      )

    case RabbitSystemMessage(module, appId, corrId, msg) =>
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
        module,  // routing key
        //new AMQP.BasicProperties.Builder().headers(headers).build(),
        props,
        upickle.write(msg).getBytes()
      )
  }
}