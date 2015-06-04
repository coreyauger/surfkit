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


  import io.surfkit.client.Base._
  import io.surfkit.client.Chat._
  import io.surfkit.client.Friends._

  case class State(friendState:FriendsState, chatState: Seq[ChatState], items: List[String], text: String)


  class Backend($: BackendScope[Unit, State], socket:Networking) extends Object with io.surfkit.client.Chat {

    def modChatState(c:io.surfkit.model.Chat.Chat)(f: (ChatState) => ChatState ) = {
      implicit val chat = c
      val cs:ChatState = $.state.chatState.filter(_.chat.chatid == chat.chatid).headOption.map(c => f(c)).getOrElse[ChatState](ChatState(io.surfkit.model.Chat.Chat(chat.chatid,chat.members,chat.entries), createChatEvents,"", "",$.state.friendState.copy(events = FriendEvents(onChatFilterChange, null, onFriendsAddToChat))))
      $.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == chat.chatid) cs else c)))
    }

    socket.addResponder("auth","friends"){
      f =>
        val friends = upickle.read[Seq[Auth.ProfileInfo]](f)
        println(s"friends.. $friends")
        $.modState(s => s.copy(friendState = FriendsState(friends,"",FriendEvents(onFilterChange,onFriendSelect, null))))
    }
    socket.addResponder("chat","create"){
      f =>
        implicit val chat = upickle.read[io.surfkit.model.Chat.Chat](f)
        // check if this chat was already created.. and if so just set the focus...
        val setFocus = $.state.chatState.filter(_.chat.chatid == chat.chatid).headOption.map{
          c =>
            // just set focus..
            true
        }.orElse[Boolean]{
          println(s"chat create.. $chat")
          socket.getChatHistory(chat.chatid)  // request some chat history...
          $.modState(s => s.copy(chatState = s.chatState :+ ChatState(chat, createChatEvents, "","",$.state.friendState.copy(events = FriendEvents(onChatFilterChange, null, onFriendsAddToChat))) ))
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
            (ChatState(c.chat.copy(entries = c.chat.entries :+ entry),c.events, c.ui, c.msg, c.friendState),true)
        }.getOrElse[(ChatState,Boolean)]( (ChatState(io.surfkit.model.Chat.Chat(entry.chatid,Nil,Seq(entry)), createChatEvents,"", "",$.state.friendState.copy(events = FriendEvents(null, null, onFriendsAddToChat)) ),false)) // TODO: is null safe for events ??
        if(alreadyOpen)$.modState(s => s.copy(chatState = s.chatState.map(c =>if(c.chat.chatid == entry.chatid) cs else c)))
        else{
          socket.getChatHistory(entry.chatid)
          $.modState(s => s.copy(chatState = s.chatState.filter(_.chat.chatid != entry.chatid) :+ cs ))
        }
    }
    socket.addResponder("chat","history"){
      f =>
        implicit val chat = upickle.read[io.surfkit.model.Chat.Chat](f)
        // TODO: compact enties from same user in same relitive time
        modChatState(chat)(c =>ChatState(c.chat.copy(members = chat.members, entries = c.chat.entries ++ chat.entries.reverse),c.events, c.ui, c.msg, c.friendState.copy(events = c.friendState.events.copy(onFilterChange = onChatFilterChange))))
    }

    // ask for friends...
    socket.getFriends

    def createChatEvents = ChatEvents(onChatMessageChange,onSendChatMessage,onChatClose,onShowAddFriends)


    def onShowAddFriends(chat:io.surfkit.model.Chat.Chat):Unit = {
      modChatState(chat)(c =>ChatState(c.chat, c.events,
        if( c.ui.contains("addFriends ")) c.ui.replace(ChatUI.ShowAddFriend,"") else c.ui + ChatUI.ShowAddFriend
        , c.msg, c.friendState))
    }

    def onChatClose(chat:io.surfkit.model.Chat.Chat):Unit = {
      $.modState(s => s.copy(chatState = s.chatState.filter(c => c.chat.chatid != chat.chatid)))
    }

    def onChatMessageChange(chat:io.surfkit.model.Chat.Chat, msg: String):Unit = {
      // TODO: need to watch this and see if this turns into a massive copy operation??
      modChatState(chat)(c =>ChatState(c.chat, c.events, c.ui, msg, c.friendState))
    }

    def onSendChatMessage(chat:io.surfkit.model.Chat.Chat, msg: String):Unit ={
      socket.sendChatMessage(chat.chatid,msg)
      modChatState(chat)(c =>ChatState(c.chat,c.events, c.ui, "", c.friendState))
      // TODO: show a ghosted msg that gets filled in when we get result from server..

    }

    def onFilterChange(e: ReactEventI):Unit = {
      val filter = e.target.value
      $.modState(_.copy(friendState = $.state.friendState.copy(filter = filter)))
    }

    def onChatFilterChange(e: ReactEventI)(implicit chat:io.surfkit.model.Chat.Chat):Unit = {
      val filter = e.target.value
      modChatState(chat)(c =>ChatState(c.chat, c.events, c.ui, c.msg, c.friendState.copy(filter = filter)))
    }

    def onFriendsAddToChat(friends:Set[Auth.ProfileInfo]):Unit = {
      socket.createChat(friends.map(_.jid))
      // close the friend adder ui
      $.modState(_.copy(chatState = $.state.chatState.map(c => c.copy(ui = c.ui.replace(ChatUI.ShowAddFriend,"")))))
    }

    def onFriendSelect(f:Auth.ProfileInfo):Unit = {
      println(s"You selected ${f.fullName}")
      println(s"You selected ${f.jid}")
      // we want to create a chat with this friend now...
      // check if chat is already open..
      socket.createChat(Set(f.jid))
    }

    def handleSubmit(e: ReactEventI) = {
      e.preventDefault()
      $.modState(s => s.copy(items = s.items :+ s.text))
    }
  }

  val SurfKitApp = ReactComponentB[Unit]("SurfKitApp")
    .initialState(State(FriendsState(Nil,"",FriendEvents(null,null,null)), Nil,  Nil, ""))
    .backend(s => new Backend(s, new Networking(Environment.userId)))
    .render((_,S,B) =>
      <.div(
        ChatModule((S.chatState,S.friendState))
      )
    ).buildU

  // TODO: factor these controls out into the Chat object...



  val ChatModule = ReactComponentB[(Seq[ChatState],FriendsState)]("ChatApp")
    .render(props => {
      val (cs,fs) = props
      <.div(^.className:="chat-module",
        <.div(^.className:="chat-cnt",
          <.ul(^.className:="nav nav-tabs",
            <.li(^.role:="presentation", ^.className:="active",<.a(^.href:="#","Friends")),
            <.li(^.role:="presentation",<.a(^.href:="#","History"))
          ),
          FriendList( (fs) )
        ),
        Chats( (cs) )
      )
    })
    .build



  def main(): Unit = {
    //React.render(SurfKitApp(), document.getElementById("content"))
  }

  @JSExport
  def run(uid:Double): Unit = {
    Environment.userId = uid.toLong
    React.render(SurfKitApp(), document.getElementById("content"))
  }

}
