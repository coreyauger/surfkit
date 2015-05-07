package io.surfkit.core.rabbitmq

import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Source, Sink, Flow}
import akka.stream.ActorFlowMaterializer

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import akka.actor.{ActorLogging, Props}
import akka.util.ByteString

import com.rabbitmq.client._

object RabbitModuleConsumer {

  lazy val sysExchange = RabbitConfig.sysExchange


  def props(channel: Channel) =
    Props(new RabbitModuleConsumer(channel))
}


class RabbitModuleConsumer(val channel: Channel) extends ActorPublisher[io.surfkit.model.ApiRequest] with ActorLogging {
  import akka.stream.actor.ActorPublisherMessage._
  import io.surfkit.core.rabbitmq.RabbitModuleConsumer._

  implicit val materializer = ActorFlowMaterializer()

  val MaxBufferSize = 100
  var buf = Vector.empty[io.surfkit.model.ApiRequest]

  private def initBindings(channel: Channel): Unit = {
    channel.exchangeDeclare(sysExchange, "direct", true)
    channel.queueDeclare(sysExchange, false, false, false, null)
    channel.queueBind(sysExchange, sysExchange, sysExchange)
    channel.basicQos(1)
  }

  val source = Source[io.surfkit.model.ApiRequest](Props[RabbitModuleConsumer](this))
  source
    //.via(Flow[io.surfkit.model.Api].map(_))
    .to(Sink.foreach{
    ret:io.surfkit.model.ApiRequest =>
      val replyProps = new AMQP.BasicProperties
      .Builder()
        .correlationId(ret.routing.id)
        .build()

      println("In Sink...")
      println(ret)
      println(s"replyTo: ${ret.routing.reply}")
      println(s"corrId: ${ret.routing.id}")
      channel.basicPublish( "", ret.routing.reply, replyProps, ret.data.getBytes())
      //channel.basicAck(ret.routing.tag, false)
  }).run()

  private def initConsumer(channel: Channel): DefaultConsumer = new DefaultConsumer(channel) {
    override def handleDelivery(
                                 consumerTag: String,
                                 envelope: Envelope,
                                 properties: AMQP.BasicProperties,
                                 body: Array[Byte]) = {
      println("** MODULE handleDelivery")
      val rawHeaders = Option(properties.getHeaders).map(_.toMap).getOrElse(Map())
      val headers = rawHeaders.mapValues(_.toString)
      val corrId =  properties.getCorrelationId
      val tagId = envelope.getDeliveryTag

      println(s"correlationId: $corrId")
      println(rawHeaders)

      val payload = ByteString(body).mkString

      self ! io.surfkit.model.ApiRequest("module","op", io.surfkit.model.ApiRoute(properties.getCorrelationId, properties.getReplyTo(), envelope.getDeliveryTag), payload)
      //self ! RabbitMessage(envelope.getDeliveryTag, properties.getReplyTo(), properties.getCorrelationId, headers, ByteString(body))

      // when we know this data is for this modules...

    }
  }

  var consumer: DefaultConsumer = null

  override def receive = {
    case job: io.surfkit.model.ApiRequest =>
      //sender() ! JobAccepted
      if (buf.isEmpty && totalDemand > 0)
        onNext(job)
      else {
        buf :+= job
        deliverBuf()
      }
    case Request(_) =>
      deliverBuf()
    case Cancel =>
      context.stop(self)
  }


  @tailrec final def deliverBuf(): Unit =
    if (totalDemand > 0) {
      /*
       * totalDemand is a Long and could be larger than
       * what buf.splitAt can accept
       */
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliverBuf()
      }
    }


  override def preStart() = {
    consumer = {
      initBindings(channel)
      log.info("SYS: initConsumer")
      val consumer = initConsumer(channel)
      channel.basicConsume(RabbitModuleConsumer.sysExchange, true, consumer)
      log.info(s"${self} started consuming from queue=$sysExchange")
      consumer
    }
  }

  override def postStop() = {
    //this might be too late and could cause losing some messages
    channel.basicCancel(consumer.getConsumerTag())
    channel.close()
  }
}