package io.surfkit.modules

import akka.actor.ActorSystem
import akka.event.Logging
import core.api.modules.SurfKitModule
import core.api.modules.SurfKitModule.{ApiResult, ApiRequest}
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.modules.HangTenSlick.FlatProviderProfile
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import slick.model.Model
import scala.concurrent.Future
import scala.util.{Failure, Success}

// required !!
import io.surfkit.model._
import io.surfkit.model.Auth._

object HangTenUserService extends App with SurfKitModule {
  implicit lazy val system = ActorSystem("hangten")
  println(s"System: $system")
  val logger = Logging(system, getClass)

  val db = HangTenSlick.db

  import io.surfkit.modules.HangTenSlick._
  import io.surfkit.model.Model


  implicit val pir    = Json.reads[Auth.PasswordInfo]
  implicit val piw    = Json.writes[Auth.PasswordInfo]

  implicit val oa1r    = Json.reads[Auth.OAuth1Info]
  implicit val oa1w    = Json.writes[Auth.OAuth1Info]

  implicit val oa2r    = Json.reads[Auth.OAuth2Info]
  implicit val oa2w    = Json.writes[Auth.OAuth2Info]

  implicit val amr    = Json.reads[Auth.AuthenticationMethod]
  implicit val amw    = Json.writes[Auth.AuthenticationMethod]

  implicit val pr     = Json.reads[Auth.ProviderProfile]
  implicit val pw     = Json.writes[Auth.ProviderProfile]

  implicit val raf    = Json.reads[Auth.FindUser]

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
            Json.toJson(HangTenSlick.Implicits.FlatProviderToProvider(provider.head)))
      }.recover {
        case _ =>
          // TODO: better case for not found then empty object?
          ApiResult(r.module, r.op, r.routing, Json.obj())
      }


    case p:Auth.ProviderProfile =>
      // this is a save or an update operation...
      HangTenSlick.getProvider(p.appId, p.providerId, p.userId).flatMap{
        case pro:Seq[HangTenSlick.FlatProviderProfile] =>
          println(s"LENGTH: ${pro.length}")
          // TODO: write an update
          Future.successful( ApiResult(r.module, r.op, r.routing, Json.toJson(Auth.SaveResponse(pro.head.id))))
      }.recoverWith {
        case _ =>
          println("could not find.. running save..")
          HangTenSlick.saveProvider(p).map( id => ApiResult(r.module, r.op, r.routing, Json.toJson(Auth.SaveResponse(id)) ) ).recover {
            case _ =>
              ApiResult(r.module, r.op, r.routing, Json.toJson(Auth.SaveResponse(0L)))
          }
      }
  }


  def mapper(r:ApiRequest):Future[ApiResult] = {
    println("Inside the mapper...")
    r.op match{
      case "find"         => validate[Auth.FindUser](r)
      case "save"         => validate[Auth.ProviderProfile](r)
      case _ =>
        logger.error("Uknown operation.")
        Future.successful(ApiResult(r.module, r.op, r.routing, Json.toJson(Auth.OAuth1Info("test","test")) ))
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