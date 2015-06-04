package io.surfkit.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

trait Base {

}

object Base{
  type ReactEvent = (ReactEventI) => Unit
  type FilterEvent = (String) => Unit

  val SearchBar = ReactComponentB[(String, ReactEvent, FilterEvent, String)]("SearchBar")
    .render(P =>{
      val (text, onChange, onSearch, icon) = P
      <.div(^.className := "input-group",
        <.input(^.className := "form-control", ^.placeholder := "filter friends", ^.onChange ==> onChange, ^.value := text ),
        <.span(^.className:="input-group-btn",
          <.button(^.className:="btn btn-default ", ^.onClick --> onSearch(text),
            <.i(^.className:=icon)
          )
        )
      )
    })
    .build

}