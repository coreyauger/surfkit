import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.http.unmarshalling.Unmarshal
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import io.surfkit.core.api.{MainActors, SurfKitApi}
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import play.api.libs.concurrent.Akka

import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DefaultJsonProtocol
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import spray.can.Http
import spray.can.server.UHttp

import io.surfkit.core.rabbitmq._
import io.surfkit.model._

/*
trait Protocols extends DefaultJsonProtocol {
  implicit val ipInfoFormat = jsonFormat5(IpInfo.apply)
  implicit val ipPairSummaryRequestFormat = jsonFormat2(IpPairSummaryRequest.apply)
  implicit val ipPairSummaryFormat = jsonFormat3(IpPairSummary.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

  lazy val coreConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.coreHost"), config.getInt("services.corePort"))

  def coreRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(coreConnectionFlow).runWith(Sink.head)

  def fetchIpInfo(ip: String): Future[Either[String, IpInfo]] = {
    coreRequest(RequestBuilding.Get(s"/geoip/$ip")).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[IpInfo].map(Right(_))
        case BadRequest => Future.successful(Left(s"$ip: incorrect IP format"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Core request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  val routes = {
    logRequestResult("akka-core-service") {
      (get & path("test")) { complete(OK -> "Lots of whatever") }


      pathPrefix("ip") {
        (get & path(Segment)) { ip =>
          complete {
            fetchIpInfo(ip).map[ToResponseMarshallable] {
              case Right(ipInfo) => ipInfo
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        } ~
          (post & entity(as[IpPairSummaryRequest])) { ipPairSummaryRequest =>
            complete {
              val ip1InfoFuture = fetchIpInfo(ipPairSummaryRequest.ip1)
              val ip2InfoFuture = fetchIpInfo(ipPairSummaryRequest.ip2)
              ip1InfoFuture.zip(ip2InfoFuture).map[ToResponseMarshallable] {
                case (Right(info1), Right(info2)) => IpPairSummary(info1, info2)
                case (Left(errorMessage), _) => BadRequest -> errorMessage
                case (_, Left(errorMessage)) => BadRequest -> errorMessage
              }
            }
          }
      }
    }
  }
}
*/

object AkkaCoreService extends App with MainActors with SurfKitApi {
  override implicit val system = ActorSystem("surfkit")
  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)
  /*

  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()


  override val logger = Logging(system, getClass)
  */

  val conf = ConfigFactory.load()

  //RabbitMQ init
  val rabbitHost = conf.getString("rabbitmq.host")
  val rabbitPort = conf.getInt("rabbitmq.port")

  //val rabbitDispatcher = Akka.system.actorOf(RabbitDispatcher.props(new RabbitAddress(rabbitHost, rabbitPort)))
  //rabbitDispatcher ! RabbitDispatcher.Connect

  val rabbitDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(rabbitHost, rabbitPort)))
  rabbitDispatcher ! RabbitDispatcher.Connect  // connect to the MQ

  //Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

}