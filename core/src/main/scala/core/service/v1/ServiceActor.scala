package io.surfkit.core.service.v1

import java.util.UUID

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import io.surfkit.core.Configuration
import play.api.libs.json._
import io.surfkit.core.rabbitmq.{RabbitSysConsumer, RabbitDispatcher}
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import spray.http.{HttpResponse, Uri}
import scala.collection.mutable
import io.surfkit.core.websocket._
import io.surfkit.model._
import io.surfkit.model.Api._

object ServiceActor {
  sealed trait Message
  case object Clear extends Message
  case class Unregister(ws : WebSocket) extends Message
  case class SendMessage(appId: String, responder: ActorRef, path : Uri.Path, data : JsValue) extends Message
}

class ServiceActor(actorProps:Props) extends Actor with ActorLogging {

  log.info("Created ServiceActor. ")

  // TODO: factor me out..
  //val dispatcher = context.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  val dispatcher = context.actorOf(actorProps)
  dispatcher ! RabbitDispatcher.Connect  // connect to the MQ

  val responders = mutable.Map[String, ActorRef]()

  // bi-key maps...
  val wsResponders = mutable.Map[String, WebSocket]()
  val wsMap =  mutable.Map[WebSocket, (String,String)]()

  override def receive = {
    case WebSocket.Open(appId, uid, ws) =>
      println("WS OPEN ....")
      if (null != ws) {
        //clients += ws
        val corrId = UUID.randomUUID().toString
        wsResponders += corrId -> ws
        wsMap += ws -> (corrId,appId)
        log.debug("registered monitor for url {}", ws.path)
        // CA - we send the connection to Auth to create or add to UserActor.
        val route = Api.Route(corrId,"",0L)
        val newActor = Auth.CreateActor(uid)
        val req = Api.Request(appId, "auth", "actor", upickle.write(newActor), route)
        dispatcher ! Api.SendSys(req.module,appId, corrId, req)
      }
    case WebSocket.Close(ws, code, reason) =>
      self ! ServiceActor.Unregister(ws)
    case WebSocket.Error(ws, ex) =>
      self ! ServiceActor.Unregister(ws)
    case WebSocket.Message(ws, msg) =>
      if (null != ws) {
        log.debug("url {} received msg '{}'", ws.path, msg)
        // get the corrId for this socket..
        val wsOp = upickle.read[Socket.Op](msg)
        wsMap.get(ws) match{
          case Some( (corrId, appId) ) =>
            val req = Api.Request(appId, wsOp.module, wsOp.op, upickle.write(wsOp.data), Api.Route(corrId,"",0L) )
            dispatcher ! Api.SendSys(req.module, appId, corrId, req)
          case None =>
            log.error("[ERROR] There is no corrId for this websocket")
        }

      }
    case ServiceActor.Unregister(ws) =>
      if (null != ws) {
        //clients -= ws
        wsResponders -= wsMap(ws)._1
        wsMap -= ws
        log.debug("unregister monitor")
        // TODO: unregister from UserActor..
      }
    case mq @ ServiceActor.SendMessage(appId, responder, path, data) =>
      log.debug("Mq {} '{}'", mq.path, mq.data)
      //for (client <- clients) client.send(msg)
      //log.debug("sent to {} clients to clear marker '{}'", clients.size, msg)
      val corrId = java.util.UUID.randomUUID().toString
      responders += corrId -> responder
      val slotOp = path.tail.toString.split('/').toList
      slotOp match{
        case module :: op :: Nil =>
          val req = Api.Request(appId, module, op, mq.data.toString, Api.Route(corrId,"",0L))
          dispatcher ! Api.SendSys(req.module, appId, corrId, req)
        case _ =>
          log.error(s"Invalid API request with path: $path")
      }


    case mq @ RabbitSysConsumer.RabbitMessage(deliveryTag, correlationId, headers, body) =>
      val bodyStr = body.decodeString("utf-8")
      println("MESSAGE FROM MODULE TO reply")
      println(s"Sending with a corrId: $correlationId message body ${bodyStr} ")
      responders.get(correlationId).map(_ ! HttpResponse(entity = bodyStr))
      responders -= correlationId
      //println(wsResponders.get(correlationId))
      wsResponders.get(correlationId).map(_.send(bodyStr))

    case whatever =>
      log.warning("RabbitMqActor unknown '{}'", whatever)
  }

}
