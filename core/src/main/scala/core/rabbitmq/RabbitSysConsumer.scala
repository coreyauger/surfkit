package io.surfkit.core.rabbitmq

import scala.collection.JavaConversions._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString

import com.rabbitmq.client._

object RabbitSysConsumer {

  lazy val sysExchange = RabbitConfig.sysExchange

  case class RabbitMessage(deliveryTag: Long, correlationId:String, headers: Map[String, String], body: ByteString)

  def props(channel: Channel, replyQueueName:String ) =
    Props(new RabbitSysConsumer(channel, replyQueueName))
}


class RabbitSysConsumer(val channel: Channel, val replyQueueName: String) extends Actor with ActorLogging {

  import io.surfkit.core.rabbitmq.RabbitSysConsumer._

  private def initBindings(channel: Channel): Unit = {
    channel.exchangeDeclare(RabbitConfig.sysExchange, "direct", true)
    log.info(s"replyQueueName: $replyQueueName")
  }

  private def initConsumer(channel: Channel): DefaultConsumer = new DefaultConsumer(channel) {
    override def handleDelivery(
                                 consumerTag: String,
                                 envelope: Envelope,
                                 properties: AMQP.BasicProperties,
                                 body: Array[Byte]) = {
      println("** handleDelivery")
      val rawHeaders = Option(properties.getHeaders).map(_.toMap).getOrElse(Map())
      val headers = rawHeaders.mapValues(_.toString)

      self ! RabbitMessage(envelope.getDeliveryTag, properties.getCorrelationId, headers, ByteString(body))
    }
  }

  var consumer: DefaultConsumer = null

  override def receive = {
    case msg: RabbitSysConsumer.RabbitMessage =>
      log.debug(s"received msg with deliveryTag ${msg.deliveryTag}")
      log.debug(s"received msg with correlationId ${msg.correlationId}")
      context.parent ! msg
  }

  override def preStart() = {
    consumer = {
      initBindings(channel)
      log.info("SYS: initConsumer")
      val consumer = initConsumer(channel)
      channel.basicConsume(replyQueueName, true, consumer)
      log.info(s"${self} started consuming from queue=$sysExchange with replyQueue=$replyQueueName")
      consumer
    }
  }

  override def postStop() = {
    //this might be too late and could cause losing some messages
    channel.basicCancel(consumer.getConsumerTag())
    channel.close()
  }
}