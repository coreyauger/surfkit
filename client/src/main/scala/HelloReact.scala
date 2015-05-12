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

object HelloReact extends JSApp{

  //case class State(ipInfos : Seq[Auth.ProviderProfile])
  //class Backend
  val net = new Networking()



  val TodoList = ReactComponentB[List[String]]("TodoList")
    .render(props => {
      def createItem(itemText: String) = <.li(itemText)
      <.ul(props map createItem)
    })
    .build

  case class State(items: List[String], text: String)

  class Backend($: BackendScope[Unit, State]) {
    def onChange(e: ReactEventI) = {
      net.test(e.target.value)
      $.modState(_.copy(text = e.target.value))
    }
    def handleSubmit(e: ReactEventI) = {
      e.preventDefault()
      $.modState(s => State(s.items :+ s.text, ""))
    }
  }

  val TodoApp = ReactComponentB[Unit]("TodoApp")
    .initialState(State(Nil, ""))
    .backend(new Backend(_))
    .render((_,S,B) =>
      <.div(
        <.h3("TODO"),
        TodoList(S.items),
        <.form(^.onSubmit ==> B.handleSubmit,
          <.input(^.onChange ==> B.onChange, ^.value := S.text),
          <.button("Add #", S.items.length + 1)
        )
      )
    ).buildU




/*
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

*/





  def main(): Unit = React.render(TodoApp(), document.getElementById("content"))

}
