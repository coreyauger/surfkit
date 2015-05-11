package io.surfkit.client

import io.surfkit.model._
import io.surfkit.model._
import japgolly.scalajs.react.vdom.all._
import japgolly.scalajs.react.{React, ReactComponentB}
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import scala.scalajs.js.Dynamic.{ global => js }

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js.JSApp
import upickle._

object HelloReact extends JSApp{

  case class State(ipInfos : Seq[Auth.ProviderProfile])

  class Backend

  val hello = ReactComponentB[Unit]("Hello")
    .initialState(State(List()))
    .backend(_ => new Backend)
    .render((_,s,_) => {
    def createIpInfo(info: Auth.ProviderProfile) = div(
      h3(s"${info.providerId}"),
      p(s"Trainer ${info.fullName} -- ${info.userId} days")
    )

    div(
      h2(s"Our course portfolio"),
      div(s.ipInfos map createIpInfo)
    )
  }).componentDidMount(scope => {
    val url = "/ip/8.8.8.8"
    Ajax.get(url).foreach { xhr => io.surfkit.model.Auth.ProviderProfile
      println(xhr.responseText)
     // val seminars = upickle.read[Seq[Seminar]](xhr.responseText)
      val info = upickle.read[Auth.ProviderProfile](xhr.responseText)
      println(info)
      scope.setState(State(Seq(info)))
    }
  }).buildU

  def main(): Unit = React.render(hello(), dom.document.getElementById("content"))

}
