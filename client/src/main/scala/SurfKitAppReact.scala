package io.surfkit.client

import io.surfkit.model.Chat
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

  type ReactEvent = (ReactEventI) => Unit
  type FilterEvent = (String) => Unit
  type UserSelectEvent = (Auth.ProfileInfo) => Unit
  type SendChatMessage = (io.surfkit.model.Chat.Chat, String) => Unit
  type ChatMessageChange = (io.surfkit.model.Chat.Chat, String) => Unit

  case class FriendsState(friends:Seq[Auth.ProfileInfo], filter:String)
  case class ChatState(chat:io.surfkit.model.Chat.Chat, onMessageChange:ChatMessageChange, onSendMessage:SendChatMessage, msg:String)
  case class State(friendState:FriendsState, chatState: Seq[ChatState], items: List[String], text: String)

  class Backend($: BackendScope[Unit, State], socket:Networking) {
    // store the "meat" of the chat outside the State to avoid massive copy overhead
    //var chats = Map[Long, List[io.surfkit.model.Chat.ChatEntry]]()

    socket.addResponder("auth","friends"){
      f =>
        val friends = upickle.read[Seq[Auth.ProfileInfo]](f)
        println(s"friends.. $friends")
        $.modState(s => s.copy(friendState = FriendsState(friends,"")))
    }
    socket.addResponder("chat","create"){
      f =>
        val chat = upickle.read[io.surfkit.model.Chat.Chat](f)
        println(s"chat create.. $chat")
        //chats  += (chat.chatid -> chat)
        $.modState(s => s.copy(chatState = s.chatState :+ ChatState(chat, onChatMessageChange,onSendChatMessage,"") ))
    }
    socket.addResponder("user","chat-send"){
      f =>
        val entry = upickle.read[io.surfkit.model.Chat.ChatEntry](f)
        println(s"GOT A CHAT SEND .. $entry")
        // TODO: need to watch this and see if this turns into a massive copy operation??
        val (cs,alreadyOpen) = $.state.chatState.filter(_.chat.chatid == entry.chatid).headOption.map{
          c =>
            (ChatState(c.chat.copy(entries = c.chat.entries :+ entry),c.onMessageChange,c.onSendMessage,c.msg),true)
        }.getOrElse[(ChatState,Boolean)]( (ChatState(io.surfkit.model.Chat.Chat(entry.chatid,Nil,Seq(entry)), onChatMessageChange,onSendChatMessage,""),false) )
        if(alreadyOpen)$.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == entry.chatid) cs else c)))
        else $.modState(s => s.copy(chatState = s.chatState.filter(_.chat.chatid != entry.chatid) :+ cs ))
    }

    // ask for friends...
    socket.getFriends


    def onChatMessageChange(chat:io.surfkit.model.Chat.Chat, msg: String):Unit = {
      // TODO: need to watch this and see if this turns into a massive copy operation??
      val cs:ChatState = $.state.chatState.filter(_.chat.chatid == chat.chatid).headOption.map{
        c =>
          ChatState(c.chat,c.onMessageChange,c.onSendMessage,msg)
      }.getOrElse[ChatState](ChatState(io.surfkit.model.Chat.Chat(chat.chatid,Nil,Nil), onChatMessageChange,onSendChatMessage,msg))
      $.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == chat.chatid) cs else c)))
    }

    def onSendChatMessage(chat:io.surfkit.model.Chat.Chat, msg: String):Unit ={
      socket.sendChatMessage(chat.chatid,msg)
      // TODO: show a ghosted msg that gets filled in when we get result from server..
    }

    def onFilterChange(e: ReactEventI):Unit = {
      val filter = e.target.value
      $.modState(_.copy(friendState = $.state.friendState.copy(filter = filter)))
    }

    def onFriendSelect(f:Auth.ProfileInfo):Unit = {
      println(s"You selected ${f.fullName}")
      println(s"You selected ${f.jid}")
      // we want to create a chat with this friend now...
      socket.createChat(f.jid)
    }

    def handleSubmit(e: ReactEventI) = {
      e.preventDefault()
      $.modState(s => s.copy(items = s.items :+ s.text))
    }
  }

  val SurfKitApp = ReactComponentB[Unit]("SurfKitApp")
    .initialState(State(FriendsState(Nil,""), Nil,  Nil, ""))
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



  val ChatEntry= ReactComponentB[(io.surfkit.model.Chat.ChatEntry)]("ChatEntry")
      .render(props => {
      val (entry) = props
      <.div(^.className:="entry",
        entry.json
      )
    })
    .build

  val ChatControls= ReactComponentB[(ChatState)]("ChatEntry")
    .render(props => {
      val (chatState) = props
      <.div(^.className:="cntrls",
        <.div("..."),
        <.input(^.`type`:="text", ^.className := "form-control", ^.placeholder := "type message", ^.onChange ==> ((e:ReactEventI) => {chatState.onMessageChange(chatState.chat,e.target.value)}), ^.value := chatState.msg ),
        <.div("send",^.onClick --> chatState.onSendMessage(chatState.chat,chatState.msg))
      )
    })
    .build


  val Chat = ReactComponentB[(ChatState)]("Chat")
    .render(props => {
      val (chatState) = props
      <.div(^.className:="chat",
        <.header("HEADER"),
        <.div(^.className:="entries",
          chatState.chat.entries.map(e => ChatEntry( (e) ))
        ),
        ChatControls( (chatState) )
      )
    })
    .build


  val Chats = ReactComponentB[(Seq[ChatState], Backend)]("Chats")
    .render(props => {
      val (chats, back) = props
      <.div(^.className:="chats",
        chats.map(c => Chat( (c) ))
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
      <.div(^.className:="chat-module",
        <.div(^.className:="chat-cnt",
          <.ul(^.className:="nav nav-tabs",
            <.li(^.role:="presentation", ^.className:="active",<.a(^.href:="#","Friends")),
            <.li(^.role:="presentation",<.a(^.href:="#","History"))
          ),
          FriendList( (s.friendState,b) )
        ),
        Chats( (s.chatState, b) )
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
