package io.surfkit.modules

import java.util.{Date, Calendar, GregorianCalendar}

import akka.actor.ActorSystem
import akka.event.Logging
import core.api.modules.SurfKitModule
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.model.Auth.UserID
import io.surfkit.model.Chat.{ChatMember, ChatEntry, ChatID}
import io.surfkit.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: CA - I would like to use the "Cake" patter to do dependency injection here
// TODO: Something like "with ChatBackend[ChatGraph] or with ChatBackend[ChatPostgres]" ..
// TODO: this will allow use to seportate the api from the implementation details.. and provide a number of other storage providers..
object SexwaxChatService extends App with SurfKitModule with ChatGraph with ChatStore{
  implicit lazy val system = ActorSystem("sexwax")
  println(s"System: $system")
  val logger = Logging(system, getClass)

  // TODO: redis cache this state?
  private var rooms = Map[ChatID,Chat.Chat]()


  private def getChat(cid:ChatID):Future[Chat.Chat] = {
    // TODO: store cached state in redis ?
    rooms.get(cid).map(Future.successful(_)).getOrElse{
      getMembersDetails(cid).map{
        members =>
          val chat = Chat.Chat(cid.chatId, members, Nil)
          rooms += cid -> chat
          chat
      }
    }
  }


  private def createOrGetChatId(uid:UserID, jid:String, members:Set[String]):Future[ChatID] = {
    // TODO: check cache ?
    findChat(members).map(_.headOption.map(c => ChatID(c))).flatMap{
      case Some(cid) =>
        Future.successful( cid )
      case _ =>
        createChat(uid.userId).map { newchatId =>
          createChatNode(newchatId, uid, members).onFailure { case e:Throwable =>logger.error(s"$e")}
          newchatId
        }
    }
  }


  def actions(r:Api.Request): PartialFunction[Model, Future[Model]] = {

    /*
    case Chat.CreateGroup(name, permission, members) =>
      //createPseudoUsers members
      createGroup(userId.toString.toLong, name, permission)
    */

    case Chat.GetHistory(chatId, maxId, offset) =>
      for{
        entries <- getChatEntriesByChatId(chatId, maxId.getOrElse(0L), offset.getOrElse(20L))
        chat <- getChat(chatId)
      }yield{
        val memberMap = chat.members.map(m => (m.jid, m)).toMap
        val chatWithEntries = Chat.Chat(chat.chatid,chat.members, entries.map(e => Chat.ChatEntry(e.chatid, e.chatentryid, e.timestamp, e.provider, e.json, memberMap.get(e.jid).getOrElse(Auth.ProfileInfo("","","Uknown","",e.jid,"")))))
        chatWithEntries
      }

    case Chat.GetChat(chatId) =>
      getMembersDetails(chatId) map {
        members =>
          val chat = Chat.Chat(chatId.chatId,members, Nil)
          chat
      }

      /*
    case Chat.GetUserGroups() =>
      getGroups(userId) map answerJson(user, "groups") onFailure(answerFail)
    */

    case Chat.MemberJoin(chatId, jid) =>
      connectMember(chatId, jid) map{
        join =>
          UnImplemented
      }

      /*
    case Chat.SetChatOrGroupName(chatId, name) =>
      setChatOrGroupName(chatId,name)
    */

    case Chat.ChatCreate(userId, members) =>
      println("ChatCreate")
      val jid = s"$userId@APPID"
      for{
        cid <- createOrGetChatId(userId,jid, (members+jid))
        chat <- getChat(cid)
      }yield{
        println(s"ChatCreate $cid, $chat")
        rooms += cid -> chat
        chat
      }


    case m @ Chat.ChatSend(userId, chatId, author, time, msg) =>
      val provider = Providers.Walkabout // TODO : app provider ??
      for{
        chat <- getChat(chatId)
        entry <- addChatEntry(chatId, author, provider, msg)
      }yield{
        println(s"SENDING CHAT TO MEMBERS... ${chat.members}")
        // TODO: we filter the member list to find the sender every time ?
        val chatEntry = Chat.ChatEntry(entry.chatid, entry.chatentryid,entry.timestamp,entry.provider,entry.json, chat.members.filter(_.jid==entry.jid).headOption.getOrElse(Auth.ProfileInfo("","","Uknown","",entry.jid,"")))
        // TODO: send an "invite" to all non-app members..
        // chat.members.filterNot(_.provider=="APPID").foreach(u => INVITE ACTION)
        // Send message to all members
        chat.members.filter(_.provider=="APPID").foreach(u => userDispatcher ! Api.SendUser(u.id.toLong,"APPID",Api.Request("chat","send",upickle.write(chatEntry), Api.Route("","",0L))))
        entry
      }


      /*
    case m @ Chat.ChatPresence(jid, status) =>
      user ! Json.toJson(m)
    */


      // TODO: this could get inefficiant when the user has a large number of chats in the history
      // TODO: I will need to revisit this at some point...
    case Chat.GetRecentChatList(uid, since) =>
      val calendar = new GregorianCalendar()
      calendar.set(Calendar.YEAR, 2000) // before this app existed ..
    //could use an Option ?
      val date = if( since == "" ) calendar.getTime else strToDate(since)
      for{
        chatIds <- getChatMembersContainingUser(uid)
        chatsGrouped = chatIds.groupBy(_.chatId).map{ case (k,v) => (k, v.map(ChatMember.toProfileInfo(_))) }
        entries <- getChatEntriesForChats(chatsGrouped.keys.toSeq, date)
        unread <- getUnreadChats(uid)
      }yield {
        val chats = entries.map{
          e =>
            val members = chatsGrouped(e.chatid)
            val author = members.filter(_.jid == e.jid).headOption.getOrElse(Auth.UnknowProfile)
            Chat.Chat(e.chatid, members, Seq(Chat.ChatEntry.create(e,author))  )
        }
        Chat.ChatList(chats)
      }

  }



  def mapper(r:Api.Request):Future[Api.Result] = {
    println("IN THE SEXWAX MAPPER ...")
    (r.op match {
      //case "groups"        => actions(userId, sender)(GetUserGroups())
      case "groupcreate"   => actions(r)(upickle.read[Chat.CreateGroup](r.data.toString))
      case "history"       => actions(r)(upickle.read[Chat.GetHistory](r.data.toString))
      case "join"          => actions(r)(upickle.read[Chat.MemberJoin](r.data.toString))
      case "list"          => actions(r)(upickle.read[Chat.GetRecentChatList](r.data.toString))
      case "get"           => actions(r)(upickle.read[Chat.GetChat](r.data.toString))
      case "presense"      => actions(r)(upickle.read[Chat.ChatPresenceRequest](r.data.toString))
      case "send"          => actions(r)(upickle.read[Chat.ChatSend](r.data.toString))
      case "create"        => actions(r)(upickle.read[Chat.ChatCreate](r.data.toString))
      case "setname"       => actions(r)(upickle.read[Chat.SetChatOrGroupName](r.data.toString))
      case _ =>
        logger.error(s"Unknown operation.  ${r.op}")
        Future.failed(new Exception(s"Unknown operation.  ${r.op}"))
    }).map(d => Api.Result(0, r.module, r.op,  upickle.write(d), r.routing))
  }

  def module = "chat"


}



