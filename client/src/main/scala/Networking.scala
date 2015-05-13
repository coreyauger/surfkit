package io.surfkit.client

import io.surfkit.model._
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

class Networking {
  val ws = new WebSocket("ws://localhost:8181/v1/ws")
  ws.onmessage = (x: MessageEvent) => {
    println("WS onmessage")
    println(x.data.toString)
    val list = upickle.read[List[Auth.ProfileInfo]](x.data.toString)
    println(list)
  }
  ws.onopen = (x: Event) => {
    println("WS connection open")
  }
  ws.onerror = (x: ErrorEvent) => println("some error has   occured " + x.message)
  ws.onclose = (x: CloseEvent) => {
    println("WS connection closed")
  }

  def test(v:String) = {
    val getFriends = upickle.write(WS.WebSocketOp("Auth","friends",Auth.GetFriends("APPID",1)))
    ws.send(getFriends)
  }
}
