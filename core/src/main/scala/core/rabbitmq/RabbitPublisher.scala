package core.rabbitmq

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, Props}
import com.rabbitmq.client.{AMQP, Channel}
import play.api.libs.json._


object RabbitPublisher {
  
  case class RabbitMessage(receiverUuid: String, provider: String, msg: JsValue)
  
  def props(channel: Channel): Props = Props(new RabbitPublisher(channel))
}

class RabbitPublisher(channel: Channel) extends Actor with ActorLogging {

  import core.rabbitmq.RabbitPublisher._
  
  channel.exchangeDeclare(RabbitConfig.userExchange, "direct", true)
  
  override def receive = {
    case RabbitMessage(userId, provider, msg) =>
      val routingKey = s"${RabbitConfig.userExchange}.$userId"
      val headers = Map("uid" -> userId, "provider" -> provider)
      channel.basicPublish(
        RabbitConfig.userExchange, 
        routingKey, 
        new AMQP.BasicProperties.Builder().headers(headers).build(), 
        msg.toString().getBytes()
      )
  }
}