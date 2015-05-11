package io.surfkit.core.service.v1

import akka.actor.{ActorRef, Actor, ActorLogging}
import core.api.modules.SurfKitModule.{ApiRoute, ApiRequest}
import io.surfkit.core.Configuration
import play.api.libs.json._
import io.surfkit.core.rabbitmq.{RabbitSysConsumer, RabbitDispatcher}
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import spray.http.{HttpResponse, Uri}
import scala.collection.mutable
import io.surfkit.core.websocket._

object RabbitMqActor {
  sealed trait MqMessage
  case object Clear extends MqMessage
  case class Unregister(ws : WebSocket) extends MqMessage
  case class Mq(responder: ActorRef, path : Uri.Path, data : JsValue) extends MqMessage
}

class RabbitMqActor extends Actor with ActorLogging {

  log.info("Created RabbitMqActor.  Trying to connect to rabbitmq...")

  val rabbitDispatcher = context.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  rabbitDispatcher ! RabbitDispatcher.Connect  // connect to the MQ

  val clients = mutable.ListBuffer[WebSocket]()
  var responders = Map[String, ActorRef]()

  override def receive = {
    case WebSocket.Open(ws) =>
      if (null != ws) {
        clients += ws
        //for (markerEntry <- markers if None != markerEntry._2)
        //  ws.send(message(markerEntry._2.get))
        log.debug("registered monitor for url {}", ws.path)
      }
    case WebSocket.Close(ws, code, reason) =>
      self ! RabbitMqActor.Unregister(ws)
    case WebSocket.Error(ws, ex) =>
      self ! RabbitMqActor.Unregister(ws)
    case WebSocket.Message(ws, msg) =>
      if (null != ws)
        log.debug("url {} received msg '{}'", ws.path, msg)
    case RabbitMqActor.Unregister(ws) =>
      if (null != ws) {
        clients -= ws
        log.debug("unregister monitor")
      }
    case mq @ RabbitMqActor.Mq(responder, path, data) =>
      log.debug("Mq {} '{}'", mq.path, mq.data)
      //for (client <- clients) client.send(msg)
      //log.debug("sent to {} clients to clear marker '{}'", clients.size, msg)
      val corrId = java.util.UUID.randomUUID().toString
      responders += corrId -> responder
      val slotOp = path.tail.toString.split('/').toList
      slotOp match{
        case module :: op :: Nil =>
          val req = ApiRequest(module, op,ApiRoute(corrId,"",0L), mq.data)
          rabbitDispatcher ! RabbitDispatcher.SendSys("appID", corrId, req)
        case _ =>
          log.error(s"Invalid API request with path: $path")
      }


    case mq @ RabbitSysConsumer.RabbitMessage(deliveryTag, correlationId, headers, body) =>
      println(s"Sending with a corrId: $correlationId")
      responders.get(correlationId).map(_ ! HttpResponse(entity = body.decodeString("utf-8")))
      responders -= correlationId

    case whatever =>
      log.warning("RabbitMqActor unknown '{}'", whatever)
  }

}
