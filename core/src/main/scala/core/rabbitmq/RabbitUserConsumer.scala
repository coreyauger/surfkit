package io.surfkit.core.rabbitmq

import io.surfkit.model.Api

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString

import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.AMQP

object RabbitUserConsumer {

  lazy val userExchange = RabbitConfig.userExchange
  
  case class RabbitMessage(deliveryTag: Long, headers: Map[String, String], body: ByteString)
  
  def props(userId: Long, userActor: ActorRef)(implicit connection: Connection) =
    Props(new RabbitUserConsumer(userId, userActor))
}


class RabbitUserConsumer(userId: Long, userActor: ActorRef)(implicit connection: Connection) extends Actor with ActorLogging {
  
  import io.surfkit.core.rabbitmq.RabbitUserConsumer._
  
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
    case msg: RabbitUserConsumer.RabbitMessage =>
      log.debug(s"received msg with deliveryTag ${msg.deliveryTag}")
      userActor ! msg

      // This pushes data back into rabbit that will go down the web socket connections to the user.
    case ret:Api.Result =>
      val replyProps = new AMQP.BasicProperties
      .Builder()
        .correlationId(ret.routing.id)
        .build()
      println("In RabbitUserConsumer Result...")
      println(ret.op)
      println(s"replyTo: ${ret.routing.reply}")
      println(s"ret.routing.id, ${ret.routing.id}")
      // TODO: looks like i need a NAMED socket queue here... check into this more.
      channel.basicPublish( "", ret.routing.reply, replyProps, upickle.write( ret ).getBytes )
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