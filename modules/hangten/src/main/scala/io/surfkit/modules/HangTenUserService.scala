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
  import io.surfkit.model.Auth._
  implicit val pir    = Json.reads[Auth.PasswordInfo]
  implicit val piw    = Json.writes[Auth.PasswordInfo]
  implicit val oa1r   = Json.reads[Auth.OAuth1Info]
  implicit val oa1w   = Json.writes[Auth.OAuth1Info]
  implicit val oa2r   = Json.reads[Auth.OAuth2Info]
  implicit val oa2w   = Json.writes[Auth.OAuth2Info]
  implicit val amr    = Json.reads[Auth.AuthenticationMethod]
  implicit val amw    = Json.writes[Auth.AuthenticationMethod]
  implicit val pr     = Json.reads[Auth.ProviderProfile]
  implicit val pw     = Json.writes[Auth.ProviderProfile]
  implicit val raf    = Json.reads[Auth.FindUser]
  implicit val rff    = Json.reads[Auth.GetFriends]
  implicit val wsr    = Json.writes[Auth.SaveResponse]

  def actions(r:Api.Request): PartialFunction[Model, Future[Api.Result]] = {
    case Auth.FindUser(appId:String, providerId: String, userId: String) =>
      logger.debug(s"Auth.FindUser($appId, $providerId, $userId)")
      HangTenSlick.getProvider(appId, providerId, userId).map {
        case provider: Seq[HangTenSlick.FlatProviderProfile] =>
          Api.Result(0,
            r.module,
            r.op,
            upickle.write(HangTenSlick.Implicits.FlatProviderToProvider(provider.head)),r.routing)
      }.recover {
        case _ =>
          Api.Result(1, r.module, r.op, "",r.routing)
      }

    case p:Auth.ProviderProfile =>
      // this is a save or an update operation...
      HangTenSlick.getProvider(p.appId, p.providerId, p.userId).flatMap{
        case pro:Seq[HangTenSlick.FlatProviderProfile] =>
          println(s"LENGTH: ${pro.length}")
          // TODO: write an update
          val h = pro.head
          addFriendsToGraph(h.userKey, p)
          Future.successful( Api.Result(0,r.module, r.op, upickle.write(Auth.SaveResponse(pro.head.id)), r.routing))
      }.recoverWith {
        case _ =>
          println("could not find.. running save..")
          HangTenSlick.saveProvider(p).map{
            id =>
              saveUserGraph(id,p).onSuccess{
                case _ =>
                  // try to import friends for this provider
                  addFriendsToGraph(id, p)
              }
              Api.Result(0, r.module, r.op, upickle.write(Auth.SaveResponse(id)), r.routing )
          }.recover {
            case _ =>
              Api.Result(1, r.module, r.op, upickle.write(Auth.SaveResponse(0L)),r.routing)
          }
      }

    case g:Auth.GetFriends =>
      getUserFriends(g.userId).map{
        jsArr =>
          Api.Result(0, r.module, r.op,  upickle.write[Seq[Auth.ProfileInfo]](jsArr), r.routing)
      }

    case g:Auth.CreateActor =>
      println("CreateActor")
      users = system.actorOf(UserActor.props(g.userId,r.routing,rabbitUserDispatcher)) :: users
      Future.successful(Api.Result(0, r.module, r.op, "",r.routing))

    case e:Auth.Echo =>
      e.users.foreach( user =>
        //println(s"Sending to user $user")
        rabbitUserDispatcher ! RabbitDispatcher.SendUser(user,"appId",Api.Request("auth","echo",upickle.write(e), Api.Route("","",0L)))
      )
      Future.successful(Api.Result(0, r.module, r.op, "",r.routing))
  }


  def mapper(r:Api.Request):Future[Api.Result] = {
    println("IN THE MAPPER ...")
    r.op match {
      case "find" => actions(r)(upickle.read[Auth.FindUser](r.data.toString))
      case "save" => actions(r)(upickle.read[Auth.ProviderProfile](r.data.toString))
      case "friends" => actions(r)(upickle.read[Auth.GetFriends](r.data.toString))
      case "actor" => actions(r)(upickle.read[Auth.CreateActor](r.data.toString))
      case "echo" => actions(r)(upickle.read[Auth.Echo](r.data.toString))
      case _ =>
        logger.error("Unknown operation.")
        Future.successful(Api.Result(1, r.module, r.op, upickle.write(Api.Error("Unknown operation.")), r.routing))
    }
  }
  val module = "auth"
  // Let's HangTen !
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