import java.net.Socket

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
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json.DefaultJsonProtocol

import im.surfkit.model._


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
      pathPrefix("test") {
        (get & path(Segment)) { test =>
          complete {
            IpInfo("test",Some("country"),None,None,None)
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

object AkkaCoreService extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}