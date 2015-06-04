package io.surfkit.client

import java.util.Date

import io.surfkit.model.Auth.SurfKitUser
import io.surfkit.model.Chat.ChatID
import io.surfkit.model._

import scala.util.{Failure, Success, Try}

//import japgolly.scalajs.react.vdom.all._
import japgolly.scalajs.react.{ReactEventI, BackendScope, React, ReactComponentB}
//import org.scalajs.dom
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom._
import scala.scalajs.js.Dynamic.{ global => js }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js.JSApp
import upickle._

object Networking {

  //case class State(ipInfos : Seq[Auth.ProviderProfile])
  //class Backend

}

class Networking(val uid:Long) {

  var isConnected = false
  var sendQueue = List[io.surfkit.model.Socket.Op]()

  // the trailing 1 is hardcoded user id for now..
  val ws = new WebSocket(s"ws://localhost:8181/v1/ws/$uid")
  ws.onmessage = (x: MessageEvent) => {
    println("WS onmessage")
    println(x.data.toString)
    val resp = upickle.read[Api.Result](x.data.toString)
    println(resp.data)
    println(resp.op)
    val key = s"${resp.module}-${resp.op}"
    // map over the responders.. the type deserialization happens with the hook
    //responders.get(key).map(l => l.foreach(r => r(resp.data)))
    responders.get(key).map(l => l.foreach(r => r(resp.data)))
  }
  ws.onopen = (x: Event) => {
    println("WS connection open")
    this.isConnected = true
    println(sendQueue)
    sendQueue.foreach(op => ws.send(upickle.write(op)))
  }
  ws.onerror = (x: ErrorEvent) => println("some error has   occured " + x.message)
  ws.onclose = (x: CloseEvent) => {
    println("WS connection closed")
  }

  var responders = Map[String, List[(String) => Unit]]()
  def addResponder(module:String, op:String)(responder:(String) => Unit) = {
    val key = s"$module-$op"
    val list:List[(String) => Unit] = responder :: responders.get(key).getOrElse(List[(String) => Unit]())
    responders += (key -> list)
  }
  // TODO: would be nice if I could get this working...
  //https://github.com/scala/pickling/issues/164
  /*
  var responders = Map[String, List[(String) => Unit]]()
  def addResponder[T](module:String, op:String)(responder:(T) => Unit) = {
    val key = s"$module-$op"
    def hook(d:String):Unit = {
      Try {
        upickle.read[T](d)
      } match{
        case Success(data) => responder(data)
        case Failure(e) =>
          println(s"Responder Failed to serialize to type..")
          println(s"${e}")
      }
    }
    val list:List[(String) => Unit] = hook _ :: responders.get(key).getOrElse(List[(String) => Unit]())
    responders += (key -> list)
  }
  */

  private def send(op:io.surfkit.model.Socket.Op) = {
    val msg = upickle.write(op)
    println("send...")
    println(msg)
    if( this.isConnected )ws.send(msg)
    else this.sendQueue = op :: this.sendQueue
  }

  def getFriends =
    send(io.surfkit.model.Socket.Op("auth","friends",Auth.GetFriends("APPID",uid)))

  def createChat(friendJIds: Set[String]) =
    send(io.surfkit.model.Socket.Op("chat","create", io.surfkit.model.Chat.ChatCreate(Auth.UserID(uid), friendJIds)))

  def getChatHistory(cid:Long) =
    send(io.surfkit.model.Socket.Op("chat","history", io.surfkit.model.Chat.GetHistory(io.surfkit.model.Chat.ChatID(cid))))

  def sendChatMessage(chatId: Long, msg:String) =
    send(io.surfkit.model.Socket.Op("chat","send", io.surfkit.model.Chat.ChatSend(Auth.UserID(uid), ChatID(chatId),s"$uid@APPID",new Date().getTime, msg)))

  def test(v:String) = {
    //val getFriends = upickle.write(io.surfkit.model.Socket.Op("Auth","friends",Auth.GetFriends("APPID",1)))
    println(s"$v")
    val echo = io.surfkit.model.Socket.Op("auth","echo",Auth.Echo("Test",List(1,2)))
    send(echo)
    println("send")
  }
}
