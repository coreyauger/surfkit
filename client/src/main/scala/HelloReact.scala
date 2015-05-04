package im.surfkit

import im.surfkit.model._
import japgolly.scalajs.react.vdom.all._
import japgolly.scalajs.react.{React, ReactComponentB}
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js.JSApp
import upickle._

object HelloReact extends JSApp{

  case class State(ipInfos : Seq[IpInfo])

  class Backend

  val helloCastillo = ReactComponentB[Unit]("HelloCastillo")
    .initialState(State(List()))
    .backend(_ => new Backend)
    .render((_,s,_) => {
    def createIpInfo(info: IpInfo) = div(
      h3(s"${info.ip}"),
      p(s"Trainer ${info.country} -- ${info.city} days")
    )

    div(
      h2(s"Our course portfolio"),
      div(s.ipInfos map createIpInfo)
    )
  })
    .componentDidMount(scope => {
    val url = "http://localhost:9999/ip/8.8.8.8"
    Ajax.get(url).foreach { xhr => im.surfkit.model.IpInfo
      println(xhr.responseText)
     // val seminars = upickle.read[Seq[Seminar]](xhr.responseText)
      val info = upickle.read[IpInfo](xhr.responseText)
      scope.setState(State(Seq(info)))
    }
  })
    .buildU

  def main(): Unit = React.render(helloCastillo(), dom.document.getElementById("content"))

}
