package io.surfkit.modules

import java.util.{Date, Calendar, GregorianCalendar}

import akka.actor.ActorSystem
import akka.event.Logging
import core.api.modules.SurfKitModule
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.model.Auth.UserID
import io.surfkit.model.Chat.{ChatEntry, ChatID}
import io.surfkit.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: CA - I would like to use the "Cake" patter to do dependency injection here
// TODO: Something like "with ChatBackend[ChatGraph] or with ChatBackend[ChatPostgres]" ..
// TODO: this will allow use to seperate the api from the implementation details.. and provide a number of other storage providers..
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


  def actions(r:Api.Request): PartialFunction[Model, Future[Api.Result]] = {

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
        Api.Result(0, r.module, r.op,  upickle.write[Chat.Chat](chatWithEntries), r.routing)
      }

    case Chat.GetChat(chatId) =>
      getMembersDetails(chatId) map {
        members =>
          val chat = Chat.Chat(chatId.chatId,members, Nil)
          Api.Result(0, r.module, r.op,  upickle.write[Chat.Chat](chat), r.routing)
      }

      /*
    case Chat.GetUserGroups() =>
      getGroups(userId) map answerJson(user, "groups") onFailure(answerFail)
    */

    case Chat.MemberJoin(chatId, memberId) =>
      connectMember(chatId, memberId) map{
        join =>
          Api.Result(0, r.module, r.op,  "", r.routing)
      }

      /*
    case Chat.SetChatOrGroupName(chatId, name) =>
      setChatOrGroupName(chatId,name)
    */

    case Chat.ChatCreate(userId, members) => {
      println("ChatCreate")
      val jid = s"$userId@APPID"
      val memberSet = (jid :: members).toSet
      for{
        cid <- createOrGetChatId(userId,jid,memberSet)
        chat <- getChat(cid)
      }yield{
        rooms += cid -> chat
        Api.Result(0, r.module, r.op,  upickle.write(chat), r.routing)
      }
    }

    case m @ Chat.ChatSend(userId, chatId, author, time, msg) => {
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
        chat.members.filter(_.provider=="APPID").foreach(u => rabbitUserDispatcher ! RabbitDispatcher.SendUser(u.id.toLong,"APPID",Api.Request("chat","send",upickle.write(chatEntry), Api.Route("","",0L))))
        Api.Result(0, r.module, r.op,  upickle.write(entry), r.routing)
      }
    }



      /*
    case m @ Chat.ChatPresence(jid, status) =>
      user ! Json.toJson(m)
    */


      /*
    case Chat.GetChatList(uid, since) =>
      val calendar = new GregorianCalendar()
      calendar.set(Calendar.YEAR, 2000) // before this app existed ..
    //could use an Option ?
    val date = if( since == "" ) calendar.getTime else service.PostgresDataService.strToDate(since)
      val both = for{
        chatIds <- getChatsContainingUser(uid.toString)
        entries <- getChatEntriesForChats(chatIds, date)
        unread <- getUnreadChats(uid.toString)
      }yield user ! Json.obj(
          "entries" -> entries.map(_.toJson),
          "unread" -> unread)

      both.onFailure(answerFail)
    */
  }


  def mapper(r:Api.Request):Future[Api.Result] = {
    println("IN THE SEXWAX MAPPER ...")
    r.op match {
      //case "groups"        => actions(userId, sender)(GetUserGroups())
      case "groupcreate"   => actions(r)(upickle.read[Chat.CreateGroup](r.data.toString))
      case "history"       => actions(r)(upickle.read[Chat.GetHistory](r.data.toString))
      case "join"          => actions(r)(upickle.read[Chat.MemberJoin](r.data.toString))
      case "list"          => actions(r)(upickle.read[Chat.GetChatList](r.data.toString))
      case "get"           => actions(r)(upickle.read[Chat.GetChat](r.data.toString))
      case "presense"      => actions(r)(upickle.read[Chat.ChatPresence](r.data.toString))
      case "send"          => actions(r)(upickle.read[Chat.ChatSend](r.data.toString))
      case "create"        => actions(r)(upickle.read[Chat.ChatCreate](r.data.toString))
      case "setname"       => actions(r)(upickle.read[Chat.SetChatOrGroupName](r.data.toString))
      case _ =>
        logger.error("Unknown operation.")
        Future.successful(Api.Result(1, r.module, r.op, upickle.write(Api.Error("Unknown operation.")), r.routing))
    }
  }

  val module = "chat"

  // Let's Wax !
  val rabbitDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  rabbitDispatcher ! RabbitDispatcher.ConnectModule(module, mapper)  // connect to the MQ

  // TODO: don't like the multiple dispatcher bit :(
  val rabbitUserDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  rabbitUserDispatcher ! RabbitDispatcher.Connect  // connect to the MQ

}





// Helper Config
object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  lazy val hostRabbit = config.getString("rabbitmq.host")
  lazy val portRabbit = config.getInt("rabbitmq.port")
}