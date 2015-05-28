package io.surfkit.core.rabbitmq

import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Source, Sink, Flow}
import akka.stream.ActorFlowMaterializer
import io.surfkit.model._
import play.api.libs.json.{JsArray, Format, Json}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import akka.actor.{ActorLogging, Props}
import akka.util.ByteString

import com.rabbitmq.client._

import scala.concurrent.Future

object RabbitModuleConsumer {

  lazy val sysExchange = RabbitConfig.sysExchange


  def props(module:String, channel: Channel, mapper: (Api.Request) => Future[Api.Result]) =
    Props(new RabbitModuleConsumer(module, channel, mapper))
}


class RabbitModuleConsumer(val module: String, val channel: Channel, val mapper: (Api.Request) => Future[Api.Result]) extends ActorPublisher[Api.Request] with ActorLogging {
  import akka.stream.actor.ActorPublisherMessage._
  import io.surfkit.core.rabbitmq.RabbitModuleConsumer._

  implicit val materializer = ActorFlowMaterializer()

  val MaxBufferSize = 100
  var buf = Vector.empty[Api.Request]

  val queue = s"$module"

  private def initBindings(channel: Channel): Unit = {
    println(s"initBindings $sysExchange")
    println(s"queue: $queue")
    channel.exchangeDeclare(sysExchange, "direct", true)
    channel.queueDeclare(queue, false, false, false, null)
    channel.queueBind(queue, sysExchange, queue)
    channel.basicQos(1)
  }

  val source = Source[Api.Request](Props[RabbitModuleConsumer](this))
  source
    .mapAsync(mapper)
    .to(Sink.foreach{
    ret:Api.Result =>
      val replyProps = new AMQP.BasicProperties
      .Builder()
        .correlationId(ret.routing.id)
        .build()
      println("In Sink...")
      println(ret.op)
      println(s"replyTo: ${ret.routing.reply}")
      println(s"corrId: ${ret.routing.id}")

      //channel.basicPublish( "", ret.routing.reply, replyProps, ret.data.toString.getBytes )
      channel.basicPublish( "", ret.routing.reply, replyProps, upickle.write( ret ).getBytes )
      //channel.basicAck(ret.routing.tag, false)
  }).run()

  private def initConsumer(channel: Channel): DefaultConsumer = new DefaultConsumer(channel) {
    override def handleDelivery(
                                 consumerTag: String,
                                 envelope: Envelope,
                                 properties: AMQP.BasicProperties,
                                 body: Array[Byte]) = {
      println("** MODULE handleDelivery")
      //val rawHeaders = Option(properties.getHeaders).map(_.toMap).getOrElse(Map())
      //val headers = rawHeaders.mapValues(_.toString)
      val corrId =  properties.getCorrelationId
      val tagId = envelope.getDeliveryTag

      println(s"correlationId: $corrId")

      val payload = ByteString(body).decodeString("utf-8")
      println(s"payload: $payload")

      val apiReq = upickle.read[Api.Request](payload)
      println(s"apiReq: $apiReq")

      self ! Api.Request( apiReq.module, apiReq.op, apiReq.data, Api.Route(properties.getCorrelationId, properties.getReplyTo(), envelope.getDeliveryTag) )
      //self ! RabbitMessage(envelope.getDeliveryTag, properties.getReplyTo(), properties.getCorrelationId, headers, ByteString(body))

      // when we know this data is for this modules...

    }
  }

  var consumer: DefaultConsumer = null

  override def receive = {
    case job: Api.Request =>
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
      //channel.basicConsume(RabbitModuleConsumer.sysExchange, true, consumer)
      channel.basicConsume(queue, true, consumer)
      log.info(s"${self} started consuming from queue=$queue")
      consumer
    }
  }

  override def postStop() = {
    //this might be too late and could cause losing some messages
    channel.basicCancel(consumer.getConsumerTag())
    channel.close()
  }
}