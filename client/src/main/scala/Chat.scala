package io.surfkit.client

import io.surfkit.client.Chat.{ChatState, ChatEvents}
import io.surfkit.client.Friends._
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

import scala.scalajs.js.JSON

/**
 * Created by suroot on 04/06/15.
 */
trait Chat{

  //implicit val $:BackendScope[Unit, ChatState]
  //implicit val socket:Networking


}

object Chat{
  type SendChatMessage = (io.surfkit.model.Chat.Chat, String) => Unit
  type ChatMessageChange = (io.surfkit.model.Chat.Chat, String) => Unit
  type ChatCloseEvent = (io.surfkit.model.Chat.Chat) => Unit
  type ChatShowAddFriends = (io.surfkit.model.Chat.Chat) => Unit

  case class ChatEvents(onMessageChange:ChatMessageChange, onSendMessage:SendChatMessage, onChatClose:ChatCloseEvent, onShowAddFriends:ChatShowAddFriends)
  case class ChatState(chat:io.surfkit.model.Chat.Chat, events:ChatEvents, ui:String, msg:String, friendState:FriendsState)

  object ChatUI{
    final val ShowAddFriend = "addFriends "
  }



  val ChatEntry= ReactComponentB[(io.surfkit.model.Chat.ChatEntry)]("ChatEntry")
    .render(props => {
      val (entry) = props
      val dyn = JSON.parse(entry.json)
      val date = new scala.scalajs.js.Date()
      date.setTime(entry.timestamp.toDouble)
      <.div(^.className:="entry",
        <.div(
          <.img(^.className:="avatar",^.src:=entry.from.avatarUrl)
        ),
        <.div(
          <.span(^.className:="uname",entry.from.fullName),
          <.span(^.className:="time",date.toLocaleString),
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
      // TODO: only want loading when user scrolls to top..
      // TODO: need to fetch more entries
      <.div(^.className:="entries",
        <.div(^.className:="loading",
          <.i(^.className:="fa fa-circle-o-notch fa-spin")
        ),
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


  val Chat = ReactComponentB[(ChatState)]("Chat")
    .render(props => {
      val (chatState) = props
      val names = chatState.chat.members.take(3).map(_.fullName).mkString(",")
      <.div(^.className:="chat",
        <.header(names,
          <.i(^.className:="fa fa-user-plus",^.onClick --> chatState.events.onShowAddFriends(chatState.chat) ),
          <.i(^.className:="fa fa-close",^.onClick --> chatState.events.onChatClose(chatState.chat) )
        ),
        if (chatState.ui.contains(ChatUI.ShowAddFriend))FriendSelector( (chatState.friendState, chatState.chat.members.toSet) ) else <.div() ,
        ChatEntryList( (chatState) ),
        ChatControls( (chatState) )
      )
    })
    .build


  val Chats = ReactComponentB[(Seq[ChatState])]("Chats")
    .render(props => {
      val (chats) = props
      <.div(^.className:="chats",
        chats.map(c => Chat( (c) ))
      )
    })
    .build



}
