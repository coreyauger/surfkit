package io.surfkit.modules

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import core.api.modules.SurfKitModule
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.core.service.v1.UserActor
import play.api.libs.json.Json
import io.surfkit.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object HangTenUserService extends App with SurfKitModule with UserGraph {
  implicit lazy val system = ActorSystem("hangten")
  println(s"System: $system")

  var users:List[ActorRef] = Nil
  val logger = Logging(system, getClass)
  val db = HangTenSlick.db


  def actions(r:Api.Request): PartialFunction[Model, Future[Model]] = {
    case Auth.FindUser(providerId: String, userId: String) =>
      logger.debug(s"Auth.FindUser(${r.appId}, $providerId, $userId)")
      HangTenSlick.getProvider(r.appId, providerId, userId).map {
        case provider: Seq[HangTenSlick.FlatProviderProfile] =>
            HangTenSlick.Implicits.FlatProviderToProvider(provider.head)
      }

    case Auth.AuthUser(uid:Long, token:String) =>
      logger.debug(s"Auth.AuthUser($uid, ${r.appId}, $token)")
      getProviderFromToken(uid, token).map{
        case provider: Seq[HangTenSlick.FlatProviderProfile] =>
          provider.headOption.map(HangTenSlick.Implicits.FlatProviderToProvider).getOrElse(Ack)
      }

    case Auth.GetProvider(uId:Long, providerId: String) =>
      logger.debug(s"Auth.GetProvider($uId, ${r.appId}, $providerId)")
      getProvider(uId, providerId).map{
        case provider: Seq[HangTenSlick.FlatProviderProfile] =>
            provider.headOption.map(HangTenSlick.Implicits.FlatProviderToProvider).getOrElse(Ack)
      }
      //HangTenSlick.getProviderForUser(uId, r.appId, providerId).map {
      //  case provider: Seq[HangTenSlick.FlatProviderProfile] =>
      //    provider.headOption.map(HangTenSlick.Implicits.FlatProviderToProvider).getOrElse(Ack)
      //}

    case p:Auth.ProviderProfile =>
      // this is a save or an update operation...
      HangTenSlick.getProvider(r.appId, p.providerId, p.userId).flatMap{
        case pro:Seq[HangTenSlick.FlatProviderProfile] =>
          println(s"LENGTH: ${pro.length}")
          // TODO: write an update
          val h = pro.head
          addFriendsToGraph(p.appId, h.userKey, p)
          Future.successful(Auth.SaveResponse(pro.head.userKey))
      }.recoverWith {
        case _ =>
          println("could not find.. doing a  save..")
          HangTenSlick.saveProvider(p).map{
            id =>
              saveUserGraph(id,p).onSuccess{
                case _ =>
                  // try to import friends for this provider
                  addFriendsToGraph(p.appId, id, p)
              }
              Auth.SaveResponse(id)
          }.recover {
            case _ =>
              Auth.SaveResponse(0L)
          }
      }

    case g:Auth.GetFriends => {
      println("IN Auth.GetFriends")
      getUserFriends(g.userId).map(Auth.ProfileInfoList(_))
    }

    case g:Auth.CreateActor =>
      println("$$$")
      println("$$$")
      println("CreateActor")
      users = system.actorOf(UserActor.props(g.userId, r.routing, userDispatcher)) :: users
      Future.successful(Ack)

    case e:Auth.Echo =>
      e.users.foreach( user =>
        userDispatcher ! Api.SendUser(user,r.appId,Api.Request(r.appId, "auth","echo",upickle.write(e), Api.Route("","",0L)))
      )
      Future.successful(Ack)
  }


  def mapper(r:Api.Request):Future[Api.Result] = {
    println("IN THE MAPPER ...")
    (r.op match {
      case "find" => actions(r)(upickle.read[Auth.FindUser](r.data.toString))
      case "token" => actions(r)(upickle.read[Auth.AuthUser](r.data.toString))
      case "provider" => actions(r)(upickle.read[Auth.GetProvider](r.data.toString))
      case "save" => actions(r)(upickle.read[Auth.ProviderProfile](r.data.toString))
      case "friends" => actions(r)(upickle.read[Auth.GetFriends](r.data.toString))
      case "actor" => actions(r)(upickle.read[Auth.CreateActor](r.data.toString))
      case "echo" => actions(r)(upickle.read[Auth.Echo](r.data.toString))
      case _ =>
        logger.error(s"Unknown operation.  ${r.op}")
        Future.failed(new Exception(s"Unknown operation.  ${r.op}"))
    }).map(d => Api.Result(0, r.module, r.op,  upickle.write(d), r.routing))
  }
  def module = "auth"


}

