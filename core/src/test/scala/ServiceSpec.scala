import akka.event.NoLogging
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.ContentTypes._
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import org.scalatest._

import io.surfkit.model._

class ServiceSpec extends FlatSpec with Matchers with ScalatestRouteTest with Service {
  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  val ip1Info = IpInfo("8.8.8.8", "United States", "Mountain View", 37.386, -122.0838)
  val ip2Info = IpInfo("8.8.4.4", "United States", "", 38.0, -97.0)
  val ipPairSummary = IpPairSummary(ip1Info, ip2Info)

  override lazy val coreConnectionFlow = Flow[HttpRequest].map { request =>
    if (request.uri.toString().endsWith(ip1Info.ip))
      HttpResponse(status = OK, entity = marshal(ip1Info))
    else if(request.uri.toString().endsWith(ip2Info.ip))
      HttpResponse(status = OK, entity = marshal(ip2Info))
    else
      HttpResponse(status = BadRequest, entity = marshal("Bad ip format"))
  }

  "Service" should "respond to single IP query" in {
    Get(s"/ip/${ip1Info.ip}") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[IpInfo] shouldBe ip1Info
    }

    Get(s"/ip/${ip2Info.ip}") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[IpInfo] shouldBe ip2Info
    }
  }

  it should "respond to IP pair query" in {
    Post(s"/ip", IpPairSummaryRequest(ip1Info.ip, ip2Info.ip)) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[IpPairSummary] shouldBe ipPairSummary
    }
  }

  it should "respond with bad request on incorrect IP format" in {
    Get("/ip/asdfg") ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[String].length should be > 0
    }

    Post(s"/ip", IpPairSummaryRequest(ip1Info.ip, "asdfg")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[String].length should be > 0
    }

    Post(s"/ip", IpPairSummaryRequest("asdfg", ip1Info.ip)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[String].length should be > 0
    }
  }
}
