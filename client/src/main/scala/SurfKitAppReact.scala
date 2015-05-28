package io.surfkit.client

import io.surfkit.model._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{ global => js }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js.JSApp
import upickle._
import scala.scalajs.js.annotation.JSExport


@JSExport
object Environment{
  var userId = 0L
}

object SurfKitAppReact extends JSApp{

  //case class State(ipInfos : Seq[Auth.ProviderProfile])
  //class Backend


  type ReactEvent = (ReactEventI) => Unit
  type FilterEvent = (String) => Unit
  type UserSelectEvent = (Auth.ProfileInfo) => Unit


  case class FriendsState(friends:Seq[Auth.ProfileInfo], filter:String)
  case class State(friendState:FriendsState,items: List[String], text: String)

  class Backend($: BackendScope[Unit, State], socket:Networking) {
    socket.addResponder("Auth","friends"){
      f =>
        val friends = f.asInstanceOf[Seq[Auth.ProfileInfo]]
        println(s"friends.. $friends")
        $.modState(s => s.copy(friendState = FriendsState(friends,"")))
    }

    socket.getFriends

    def onFilterChange(e: ReactEventI):Unit = {
      val filter = e.target.value
      $.modState(_.copy(friendState = $.state.friendState.copy(filter = filter)))
    }

    def onFriendSelect(f:Auth.ProfileInfo):Unit = {
      println(s"You selected ${f.fullName}")

    }

    def handleSubmit(e: ReactEventI) = {
      e.preventDefault()
      $.modState(s => s.copy(items = s.items :+ s.text))
    }
  }

  val SurfKitApp = ReactComponentB[Unit]("SurfKitApp")
    .initialState(State(FriendsState(Nil,""), Nil, ""))
    .backend(s => new Backend(s, new Networking(Environment.userId)))
    .render((_,S,B) =>
      <.div(
        ChatModule((S,B))
      )
    ).buildU


  val SearchBar = ReactComponentB[(String, ReactEvent, FilterEvent)]("SearchBar")
    .render(P =>{
    val (text, onChange, onSearch) = P
      <.div(^.className := "input-group",
        <.input(^.className := "form-control", ^.placeholder := "search", ^.onChange ==> onChange, ^.value := text ),
        <.span(^.className:="input-group-btn",<.button(^.className:="btn btn-default ", ^.onClick --> onSearch(text),<.i(^.className:="fa fa-search") ))

      )
    })
    .build




  val FriendCard = ReactComponentB[(Auth.ProfileInfo, UserSelectEvent)]("FriendCard")
    .render(props => {
      val (friend,onSelect) = props
      <.div(^.className:="friend-card",^.onClick --> onSelect(friend),
        <.img(^.src:= friend.avatarUrl, ^.className:="avatar"),
        <.span(friend.fullName)
      )
    })
    .build


  val FriendList = ReactComponentB[(FriendsState, Backend)]("FriendList")
    .render(props => {
      val (friends,b) = props
      val name = friends.filter.toLowerCase
       <.div(^.className:="friend-list",
         <.header(^.className:="tools",
           SearchBar( (friends.filter,b.onFilterChange, null) )
         ),
         <.div(friends.friends.filter(_.fullName.toLowerCase.contains(name)).map(f => FriendCard( (f,b.onFriendSelect) ) ))
      )
    })
    .build

  val ChatModule = ReactComponentB[(State,Backend)]("ChatApp")
    .render(props => {
      val (s,b) = props
      <.div(^.className:="chat-cnt",
        <.ul(^.className:="nav nav-tabs",
          <.li(^.role:="presentation", ^.className:="active",<.a(^.href:="#","Friends")),
          <.li(^.role:="presentation",<.a(^.href:="#","History"))
        ),
        FriendList( (s.friendState,b) )
      )
    })
    .build



  def main(): Unit = {
    React.render(SurfKitApp(), document.getElementById("content"))
  }

  @JSExport
  def run(uid:Double): Unit = {
    Environment.userId = uid.toLong
    React.render(SurfKitApp(), document.getElementById("content"))
  }

}
