package io.surfkit.client

import io.surfkit.client.Chat.{ChatState, ChatEvents}
import japgolly.scalajs.react.BackendScope

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
  case class ChatState(chat:io.surfkit.model.Chat.Chat, events:ChatEvents, ui:String, msg:String)

  object ChatUI{
    final val ShowAddFriend = "addFriends "
  }


}
