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
import io.surfkit.model._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SexwaxChatService extends App with SurfKitModule with ChatGraph with ChatStore{
  implicit lazy val system = ActorSystem("sexwax")
  println(s"System: $system")
  val logger = Logging(system, getClass)

  // TODO: redis cache this state?
  var rooms = Map[ChatID,Set[String]]()




  def createOrGetChatId(uid:UserID, jid:String, members:Set[String]):Future[ChatID] = {
    // TODO: check cache ?
    findChat(members).map(_.headOption.map(c => ChatID(c))).flatMap{
      case Some(cid) =>
        println(s"found some($cid)")
        Future.successful( cid )
      case _ =>
        println("createChat")
        createChat(uid.userId).map { newchatId =>
          createChatNode(newchatId, uid, members).onFailure { case e:Throwable =>
            //user ! MessageProcessError(Json.obj("msg" -> e.getMessage()))
            println("ERROR....")
            logger.error(s"$e")
          }
          println(s"NEW CHAT ID: $newchatId")
          rooms += newchatId -> members
          // TODO: store cached state in redis ?
          newchatId
        }
    }

  }



  def sendMessage(m:Chat.ChatSend) ={

  }

  def actions(r:Api.Request): PartialFunction[Model, Future[Api.Result]] = {

    /*
    case Chat.CreateGroup(name, permission, members) =>
      //createPseudoUsers members
      createGroup(userId.toString.toLong, name, permission)
    */

    /*
    case Chat.GetHistory(chatId, maxId, offset) =>
      //should be able to run in parallel
      //http://buransky.com/scala/scala-for-comprehension-with-concurrently-running-futures/
      //but doest not
      //Future.sequence could be nice as well if postgres can return a Future[JsValue] to match neo4j api
      val fhistory = getChatEntriesByChatId(chatId, maxId.getOrElse(0L), offset.getOrElse(20L))
      val fmembers = getMembersDetails(chatId.toString)
      val both = for {
        members <- fmembers
        history <- fhistory
      } yield user ! Json.obj(
          "id" -> chatId,
          "entries" -> history.map(_.toJson),
          "members" -> members)

      both.onFailure (answerFail(r))
      */

    case Chat.GetMembers(chatId) =>
      getMembersDetails(chatId.toString) map {
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
      createOrGetChatId(userId,jid,memberSet).map{
        chatId =>
          Api.Result(0, r.module, r.op,  upickle.write(Chat.Chat(chatId.chatId, Nil, Nil)), r.routing)
      }
    }


    case m @ Chat.ChatSend(userId, chatId, author, time, msg) => {
      val provider = Providers.Walkabout // TODO : app provider ??
      val now = new Date()
      addChatEntry(chatId, author, Providers.Walkabout, msg).map{
        entry =>
          println(s"Chat Entry $entry")
          // TODO: get chat members from cache..
          val members = List(1,2,3)
          members.foreach{
            user =>
              rabbitUserDispatcher ! RabbitDispatcher.SendUser(user,"APPID",Api.Request("chat","send",upickle.write(entry), Api.Route("","",0L)))
          }
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
      case "members"       => actions(r)(upickle.read[Chat.GetMembers](r.data.toString))
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