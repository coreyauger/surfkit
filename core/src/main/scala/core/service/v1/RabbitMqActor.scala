package io.surfkit.core.service.v1

import java.util.UUID

import akka.actor.{ActorRef, Actor, ActorLogging}
import io.surfkit.core.Configuration
import play.api.libs.json._
import io.surfkit.core.rabbitmq.{RabbitSysConsumer, RabbitDispatcher}
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import spray.http.{HttpResponse, Uri}
import scala.collection.mutable
import io.surfkit.core.websocket._
import io.surfkit.model._
import io.surfkit.model.Api._

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

  //val clients = mutable.ListBuffer[WebSocket]()
  val responders = mutable.Map[String, ActorRef]()

  // bi-key maps...
  val wsResponders = mutable.Map[String, WebSocket]()
  val wsMap =  mutable.Map[WebSocket, String]()

  override def receive = {
    case WebSocket.Open(ws) =>
      println("WS OPEN ....")
      if (null != ws) {
        //clients += ws
        val corrId = UUID.randomUUID().toString
        wsResponders += corrId -> ws
        wsMap += ws -> corrId
        //for (markerEntry <- markers if None != markerEntry._2)
        //  ws.send(message(markerEntry._2.get))
        log.debug("registered monitor for url {}", ws.path)
      }
    case WebSocket.Close(ws, code, reason) =>
      self ! RabbitMqActor.Unregister(ws)
    case WebSocket.Error(ws, ex) =>
      self ! RabbitMqActor.Unregister(ws)
    case WebSocket.Message(ws, msg) =>
      if (null != ws) {
        log.debug("url {} received msg '{}'", ws.path, msg)
        // get the corrId for this socket..
        val wsOp = upickle.read[WS.WebSocketOp](msg)
        wsMap.get(ws) match{
          case Some(corrId) =>
            // TODO: this sux below => Json.parse( upickle.write(wsOp.data) )
            val req = Api.ApiRequest(wsOp.module, wsOp.op, Api.ApiRoute(corrId,"",0L), upickle.write(wsOp.data) )
            rabbitDispatcher ! RabbitDispatcher.SendSys("appID", corrId, req)
          case None =>
            log.error("[ERROR] There is no corrId for this websocket")
        }

      }
    case RabbitMqActor.Unregister(ws) =>
      if (null != ws) {
        //clients -= ws
        wsResponders -= wsMap(ws)
        wsMap -= ws
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
          val req = Api.ApiRequest(module, op,Api.ApiRoute(corrId,"",0L), mq.data.toString)
          rabbitDispatcher ! RabbitDispatcher.SendSys("appID", corrId, req)
        case _ =>
          log.error(s"Invalid API request with path: $path")
      }


    case mq @ RabbitSysConsumer.RabbitMessage(deliveryTag, correlationId, headers, body) =>
      val bodyStr = body.decodeString("utf-8")
      println(s"Sending with a corrId: $correlationId message body ${bodyStr} ")
      responders.get(correlationId).map(_ ! HttpResponse(entity = bodyStr))
      responders -= correlationId
      println(wsResponders.get(correlationId))
      wsResponders.get(correlationId).map(_.send(bodyStr))

    case whatever =>
      log.warning("RabbitMqActor unknown '{}'", whatever)
  }

}
