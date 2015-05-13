package io.surfkit.modules

import akka.actor.ActorSystem
import akka.event.Logging
import core.api.modules.SurfKitModule
import io.surfkit.model.Api._
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import play.api.libs.json.Json
import io.surfkit.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object HangTenUserService extends App with SurfKitModule with UserGraph {
  implicit lazy val system = ActorSystem("hangten")
  println(s"System: $system")
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

  def actions(r:ApiRequest): PartialFunction[Model, Future[ApiResult]] = {
    case Auth.FindUser(appId:String, providerId: String, userId: String) =>
      logger.debug(s"Auth.FindUser($appId, $providerId, $userId)")
      HangTenSlick.getProvider(appId, providerId, userId).map {
        case provider: Seq[HangTenSlick.FlatProviderProfile] =>
          ApiResult(
            r.module,
            r.op,
            r.routing,
            upickle.write(HangTenSlick.Implicits.FlatProviderToProvider(provider.head)))
      }.recover {
        case _ =>
          // TODO: better case for not found then empty object?
          ApiResult(r.module, r.op, r.routing, "")
      }

    case p:Auth.ProviderProfile =>
      // this is a save or an update operation...
      HangTenSlick.getProvider(p.appId, p.providerId, p.userId).flatMap{
        case pro:Seq[HangTenSlick.FlatProviderProfile] =>
          println(s"LENGTH: ${pro.length}")
          // TODO: write an update
          val h = pro.head
          addFriendsToGraph(h.userKey, p)
          Future.successful( ApiResult(r.module, r.op, r.routing, upickle.write(Auth.SaveResponse(pro.head.id))))
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
              ApiResult(r.module, r.op, r.routing, upickle.write(Auth.SaveResponse(id)) )
          }.recover {
            case _ =>
              ApiResult(r.module, r.op, r.routing, upickle.write(Auth.SaveResponse(0L)))
          }
      }

    case g:Auth.GetFriends =>
      getUserFriends(g.userId).map{
        jsArr =>
          print("#############################")
          //println(jsArr)
          println(upickle.write[Seq[Auth.ProfileInfo]](jsArr))
          ApiResult(r.module, r.op, r.routing, upickle.write[Seq[Auth.ProfileInfo]](jsArr))
      }
  }


  def mapper(r:ApiRequest):Future[ApiResult] = {
    println("IN THE MAPPER ...")
    r.op match {
      case "find" => actions(r)(upickle.read[Auth.FindUser](r.data.toString))
      case "save" => actions(r)(upickle.read[Auth.ProviderProfile](r.data.toString))
      case "friends" => actions(r)(upickle.read[Auth.GetFriends](r.data.toString))
      case _ =>
        logger.error("Unknown operation.")
        // TODO: ...
        Future.successful(ApiResult(r.module, r.op, r.routing, ""))
    }
  }

  // Let's HangTen !
  val rabbitDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(Configuration.hostRabbit, Configuration.portRabbit)))
  rabbitDispatcher ! RabbitDispatcher.ConnectModule(mapper)  // connect to the MQ
}





// Helper Config
object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  lazy val hostRabbit = config.getString("rabbitmq.host")
  lazy val portRabbit = config.getInt("rabbitmq.port")
}