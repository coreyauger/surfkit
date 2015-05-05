package io.surfkit.core.rabbitmq

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString

import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.AMQP

object RabbitConsumer {

  lazy val userExchange = RabbitConfig.userExchange
  
  case class RabbitMessage(deliveryTag: Long, headers: Map[String, String], body: ByteString)
  
  def props(userId: String, userActor: ActorRef)(implicit connection: Connection) = 
    Props(new RabbitConsumer(userId, userActor))
}


class RabbitConsumer(userId: String, userActor: ActorRef)(implicit connection: Connection) extends Actor with ActorLogging {
  
  import io.surfkit.core.rabbitmq.RabbitConsumer._
  
  val queue = s"${RabbitConfig.userExchange}.$userId"
  
  private def initBindings(channel: Channel): Unit = {
    channel.exchangeDeclare(userExchange, "direct", true)
    channel.queueDeclare(queue, true, false, false, Map[String, java.lang.Object]())
    channel.queueBind(queue, userExchange, queue)
  }
  
  private def initConsumer(channel: Channel): DefaultConsumer = new DefaultConsumer(channel) {
    override def handleDelivery(
        consumerTag: String, 
        envelope: Envelope, 
        properties: AMQP.BasicProperties, 
        body: Array[Byte]) = {
      val rawHeaders = Option(properties.getHeaders).map(_.toMap).getOrElse(Map()) 
      val headers = rawHeaders.mapValues(_.toString)
          
      self ! RabbitMessage(envelope.getDeliveryTag, headers, ByteString(body))
    }
  }
  
  var channel: Channel = null
  var consumer: DefaultConsumer = null
  
      
  override def receive = {
    case msg: RabbitConsumer.RabbitMessage =>
      log.debug(s"received msg with deliveryTag ${msg.deliveryTag}")
      userActor ! msg
  }
  
  override def preStart() = {
    channel = connection.createChannel()
    consumer = {
      initBindings(channel)
      val consumer = initConsumer(channel)
      channel.basicConsume(queue, true, consumer)
      log.info(s"$userActor started consuming from queue=$queue")
      consumer
    }
  }
  
  override def postStop() = {
    //this might be too late and could cause losing some messages
    channel.basicCancel(consumer.getConsumerTag())
    channel.close()
  }
}