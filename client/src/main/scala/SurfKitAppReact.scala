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
import scala.scalajs.js.{JSON, JSApp}
import upickle._
import scala.scalajs.js.annotation.JSExport


@JSExport
object Environment{
  var userId = 0L
}

object SurfKitAppReact extends JSApp{

  type ReactEvent = (ReactEventI) => Unit
  type FilterEvent = (String) => Unit
  type FriendSelectEvent = (Auth.ProfileInfo) => Unit
  type SendChatMessage = (io.surfkit.model.Chat.Chat, String) => Unit
  type ChatMessageChange = (io.surfkit.model.Chat.Chat, String) => Unit
  type ChatCloseEvent = (io.surfkit.model.Chat.Chat) => Unit

  case class FriendsState(friends:Seq[Auth.ProfileInfo], filter:String)
  case class ChatEvents(onMessageChange:ChatMessageChange, onSendMessage:SendChatMessage, onChatClose:ChatCloseEvent)
  case class ChatState(chat:io.surfkit.model.Chat.Chat, events:ChatEvents , msg:String)
  case class State(friendState:FriendsState, chatState: Seq[ChatState], items: List[String], text: String)

  class Backend($: BackendScope[Unit, State], socket:Networking) {
    // store the "meat" of the chat outside the State to avoid massive copy overhead
    //var chats = Map[Long, List[io.surfkit.model.Chat.ChatEntry]]()

    def modChatState(chat:io.surfkit.model.Chat.Chat)(f: (ChatState) => ChatState ) = {
      val cs:ChatState = $.state.chatState.filter(_.chat.chatid == chat.chatid).headOption.map(c => f(c)).getOrElse[ChatState](ChatState(io.surfkit.model.Chat.Chat(chat.chatid,chat.members,chat.entries), ChatEvents(onChatMessageChange,onSendChatMessage,onChatClose),""))
      $.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == chat.chatid) cs else c)))
    }

    socket.addResponder("auth","friends"){
      f =>
        val friends = upickle.read[Seq[Auth.ProfileInfo]](f)
        println(s"friends.. $friends")
        $.modState(s => s.copy(friendState = FriendsState(friends,"")))
    }
    socket.addResponder("chat","create"){
      f =>
        val chat = upickle.read[io.surfkit.model.Chat.Chat](f)
        // check if this chat was already created.. and if so just set the focus...
        val setFocus = $.state.chatState.filter(_.chat.chatid == chat.chatid).headOption.map{
          c =>
            // just set focus..
            true
        }.orElse[Boolean]{
          println(s"chat create.. $chat")
          socket.getChatHistory(chat.chatid)  // request some chat history...
          $.modState(s => s.copy(chatState = s.chatState :+ ChatState(chat, ChatEvents(onChatMessageChange,onSendChatMessage,onChatClose), "") ))
          Some(false)
        }


    }
    socket.addResponder("user","chat-send"){
      f =>
        val entry = upickle.read[io.surfkit.model.Chat.ChatEntry](f)
        println(s"GOT A CHAT SEND .. $entry")
        // TODO: need to watch this and see if this turns into a massive copy operation??
        val (cs,alreadyOpen) = $.state.chatState.filter(_.chat.chatid == entry.chatid).headOption.map{
          c =>
            (ChatState(c.chat.copy(entries = c.chat.entries :+ entry),c.events, c.msg),true)
        }.getOrElse[(ChatState,Boolean)]( (ChatState(io.surfkit.model.Chat.Chat(entry.chatid,Nil,Seq(entry)), ChatEvents(onChatMessageChange,onSendChatMessage,onChatClose),""),false) )
        if(alreadyOpen)$.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == entry.chatid) cs else c)))
        else $.modState(s => s.copy(chatState = s.chatState.filter(_.chat.chatid != entry.chatid) :+ cs ))
    }
    socket.addResponder("chat","history"){
      f =>
        val chat = upickle.read[io.surfkit.model.Chat.Chat](f)
        // TODO: compact enties from same user in same relitive time
        modChatState(chat)(c =>ChatState(c.chat.copy(entries = c.chat.entries ++ chat.entries.reverse),c.events, c.msg))
    }

    // ask for friends...
    socket.getFriends





    def onChatClose(chat:io.surfkit.model.Chat.Chat):Unit = {
      $.modState(s => s.copy(chatState = s.chatState.filter(c => c.chat.chatid != chat.chatid)))
    }

    def onChatMessageChange(chat:io.surfkit.model.Chat.Chat, msg: String):Unit = {
      // TODO: need to watch this and see if this turns into a massive copy operation??
      modChatState(chat)(c =>ChatState(c.chat, c.events, msg))
    }

    def onSendChatMessage(chat:io.surfkit.model.Chat.Chat, msg: String):Unit ={
      socket.sendChatMessage(chat.chatid,msg)
      modChatState(chat)(c =>ChatState(c.chat,c.events,""))
      // TODO: show a ghosted msg that gets filled in when we get result from server..

    }

    def onFilterChange(e: ReactEventI):Unit = {
      val filter = e.target.value
      $.modState(_.copy(friendState = $.state.friendState.copy(filter = filter)))
    }

    def onFriendAddToChat(f:Auth.ProfileInfo):Unit = {
      println(s"You selected ${f.fullName}")
      println(s"You selected ${f.jid}")
      // we want to create a chat with this friend now...
      // check if chat is already open..
      socket.createChat(List("1@APPID","2@APPID","3@APPID"))
    }

    def onFriendSelect(f:Auth.ProfileInfo):Unit = {
      println(s"You selected ${f.fullName}")
      println(s"You selected ${f.jid}")
      // we want to create a chat with this friend now...
      // check if chat is already open..
      socket.createChat(List(f.jid))
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


  val SearchBar = ReactComponentB[(String, ReactEvent, FilterEvent, String)]("SearchBar")
    .render(P =>{
    val (text, onChange, onSearch, icon) = P
      <.div(^.className := "input-group",
        <.input(^.className := "form-control", ^.placeholder := "search", ^.onChange ==> onChange, ^.value := text ),
        <.span(^.className:="input-group-btn",
          <.button(^.className:="btn btn-default ", ^.onClick --> onSearch(text),
            <.i(^.className:=icon)
          )
        )
      )
    })
    .build



  val FriendCard = ReactComponentB[(Auth.ProfileInfo, FriendSelectEvent)]("FriendCard")
    .render(props => {
      val (friend,onSelect) = props
      <.div(^.className:="friend-card",^.onClick --> onSelect(friend),
        <.img(^.src:= friend.avatarUrl, ^.className:="avatar"),
        <.span(friend.fullName)
      )
    })
    .build


  val FriendList = ReactComponentB[(FriendsState, ReactEvent, FriendSelectEvent)]("FriendList")
    .render(props => {
      val (friends,onFilterChange, onFriendSelect) = props
      val name = friends.filter.toLowerCase
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,onFilterChange, null,"fa fa-search") )
        ),
        <.div(friends.friends.filter(_.fullName.toLowerCase.contains(name)).map(f => FriendCard( (f,onFriendSelect) ) ))
      )
    })
    .build


  val FriendSelector = ReactComponentB[(FriendsState, Set[Auth.ProfileInfo], ReactEvent, FriendSelectEvent)]("FriendFinder")
    .render(props => {
      val (friends,members,onFilterChange, onFriendSelect) = props
      val name = friends.filter.toLowerCase
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,onFilterChange, null,"fa fa-plus") )
        ),
        <.div(friends.friends.filter(_.fullName.toLowerCase.contains(name)).map(f => FriendCard( (f,onFriendSelect) ) ))
      )
    })
    .build


  val ChatEntry= ReactComponentB[(io.surfkit.model.Chat.ChatEntry)]("ChatEntry")
      .render(props => {
      val (entry) = props
      val dyn = JSON.parse(entry.json)
      <.div(^.className:="entry",
        <.div(
          <.img(^.className:="avatar",^.src:=entry.from.avatarUrl)
        ),
        <.div(
          <.span(^.className:="uname",entry.from.fullName),
          <.span(^.className:="time",entry.timestamp),
          <.span(dyn.msg.toString)
        )
      )
    })
    .build

  val ChatControls= ReactComponentB[(ChatState)]("ChatEntry")
    .render(props => {
      val (chatState) = props
      <.div(^.className:="cntrls input-group",
        <.span(^.className:="fa fa-ellipsis-v input-group-addon"),
        <.input(^.`type`:="text", ^.className := "form-control", ^.placeholder := "type message", ^.onChange ==> ((e:ReactEventI) => {chatState.events.onMessageChange(chatState.chat,e.target.value)}), ^.value := chatState.msg ),
        <.span(^.className:="fa fa-paper-plane input-group-addon",^.onClick --> chatState.events.onSendMessage(chatState.chat,chatState.msg))
      )
    })
    .build

  val ChatEntryList = ReactComponentB[(ChatState)]("ChatEntryList")
    .render(props => {
      val (chatState) = props
      <.div(^.className:="entries",
        chatState.chat.entries.map(e => ChatEntry( (e) ))
      )
    }).componentWillUpdate( (self, prevProps, prevState) =>{
      val node = self.getDOMNode()
      val shouldScroll = (node.scrollTop + node.offsetHeight) == node.scrollHeight
      //self.modState() TODO: see if we can store the shouldScroll ?
    }).componentDidUpdate( (self, prevProps, prevState) =>{
      val node = self.getDOMNode()
      node.scrollTop = node.scrollHeight
    })
    .build


  val Chat = ReactComponentB[(ChatState, FriendsState, Backend)]("Chat")
    .render(props => {
      val (chatState,friendStat,b) = props
      val names = chatState.chat.members.take(3).map(_.fullName).mkString(",")
      <.div(^.className:="chat",
        <.header(names,
          <.i(^.className:="fa fa-user-plus",^.onClick --> chatState.events.onChatClose(chatState.chat) ),
          <.i(^.className:="fa fa-close",^.onClick --> chatState.events.onChatClose(chatState.chat) )
        ),
        FriendSelector( (friendStat, chatState.chat.members.toSet, b.onFilterChange, b.onFriendAddToChat) ),
        ChatEntryList( (chatState) ),
        ChatControls( (chatState) )
      )
    })
    .build


  val Chats = ReactComponentB[(Seq[ChatState], FriendsState, Backend)]("Chats")
    .render(props => {
      val (chats, friends, back) = props
      <.div(^.className:="chats",
        chats.map(c => Chat( (c,friends,back) ))
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
          FriendList( (s.friendState,b.onFilterChange, b.onFriendSelect) )
        ),
        Chats( (s.chatState, s.friendState, b) )
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
