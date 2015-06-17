package core.common

import com.ning.http.client.AsyncHttpClientConfig
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.ws.{DefaultWSClientConfig, WS}
import play.api.libs.ws.ning.{NingWSClient, NingAsyncHttpClientConfigBuilder}

import scala.concurrent.Future
import scala.util.Try

trait NeoService {

  val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
  val builder = new AsyncHttpClientConfig.Builder(config)
  val ws = new NingWSClient(builder.build)

  sealed class Neo4JServer(val host:String, val port:Int, val path:String){
    def url(part:String) = {
      "http://%s:%s%s%s".format(host,port,path,part)
    }
  }


  def batchCypher(statments: List[String])(implicit neoServer:Neo4JServer): Future[JsValue] = {
    val jsonStatments: List[JsValue] = statments.map{
      case s:String =>  Json.obj("statement" -> s)
    }
    val req = ws.url(neoServer.url("transaction/commit"))
      .withHeaders( ("Accept","application/json; charset=UTF-8"), ("Content-Type", "application/json") )
      .post(Json.obj("statements" -> jsonStatments))
    req.map { res =>
      println(res.json)
      res.json
    }
  }



  case class Q(q: String, params: JsObject = Json.obj()) {

    type Cols = List[String]
    type Values = List[List[String]]
    type ValuesNode = List[JsValue]

    case class Neo4PlayException(msg: String) extends Exception(msg)

    case class ResultError(code: String, message: String)

    implicit val errorReads: Reads[ResultError] = (
      (JsPath \ "code").read[String] and
      (JsPath \\ "message").read[String]
    )(ResultError.apply _)

    def use(params: (String, JsValueWrapper)*): Q = Q(q, Json.obj(params: _*))

    def getSingle[T](column: String)(implicit neoServer:Neo4JServer, reader: Reads[T]) = neoPost.map(l => (l.head \ column).as[T])


    def getMultiple[T](column: String)(implicit neoServer:Neo4JServer, reader: Reads[T]) = neoPost.map(l => l.map(js => (js \ column).as[T]))

    def getOneJs(implicit neoServer:Neo4JServer): Future[JsObject] = neoPost.map(_.head)

    def getOneOptJs(implicit neoServer:Neo4JServer): Future[Option[JsObject]] = neoPost.map(_.headOption)

    def getOne[T](implicit neoServer:Neo4JServer, r: Reads[T]): Future[T] = neoPost.map(_.head.as[T])

    def getManyJs(implicit neoServer:Neo4JServer): Future[JsArray] = neoPost.map(JsArray)

    def getMany[T](implicit neoServer:Neo4JServer, r: Reads[T]): Future[Seq[T]] = neoPost.map { results =>
      /*
      println(results)
      println(s"READS $r")
      try{
        results.map{a => println(a);a.as[T]}
      } catch{
        case t => println(t)
      }
      */
      results.map(_.as[T])


    }

    def run()(implicit neoServer:Neo4JServer) = neoPost

    import scala.reflect.runtime.universe._
    def getFields[T: TypeTag] = typeOf[T].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.toString
    }.toList

    def withReturn[T: TypeTag](node: String = "n"): Q = {
      val newq = q + getFields[T].map( f => s"$node.$f as $f").mkString(" RETURN ", ", ", ";")
      Q(newq, params)
    }

    def transactWith(queries: Q*) = ???

    /*
    * Because of the heterogeneous response type. We can only parse queries that
    * RETURN a single node like RETURN p,
    * or RETURN projections like RETURN p.id, p.name as provider
    * No RETURN will always returns an empty Seq
    *
    * Neo4j always use http 200 or 201 code. So no 40x for errors, we need to inspect the json "errors" field
    */
    private def neoPost()(implicit neoServer:Neo4JServer): Future[Seq[JsObject]] = {
      //println(Json.obj("statements" -> Json.arr(
      //  Json.obj("statement" -> q.stripMargin, "parameters" -> params))
      //))
      ws.url(neoServer.url("transaction/commit"))
        .withHeaders(("Accept", "application/json; charset=UTF-8"), ("Content-Type", "application/json"))
        .post(Json.obj("statements" -> Json.arr(
        Json.obj("statement" -> q.stripMargin, "parameters" -> params))
      )).map { res =>
        val errors = (res.json \ "errors").as[Seq[ResultError]]
        val results = (res.json \ "results")(0)
        val values = (results \\ "row").map(_.as[Seq[JsValue]]).filter(_ != JsNull)
        (errors, values) match {
          case (err, _) if !err.isEmpty => throw Neo4PlayException(s"neo4j exception: ${errors.head.message} - ${errors.head.code}")
          case (_, rows) if rows.isEmpty => Seq.empty
          case _ => parseResult(values, res.json)
        }
      }

    }

    private def parseResult(values: Seq[Seq[JsValue]], json: JsValue) = {
      println(s"values $values")
      println(s"json $json")
      val results = (json \ "results")(0)
      val cols = (results \ "columns").as[Seq[String]]
      values.head.head match {
        case str: JsString => values.map(row => JsObject(cols.zip(row)))
        case num: JsNumber =>
          println(s"values: ${values.map(row => JsObject(cols.zip(row)))}")
          val ls:List[JsObject] = values.map(row => JsObject(cols.zip(row))).toList
          //val vv = (values.map(row => JsObject(cols.zip(row)))).map( l => (l.head \ column)
          values.map(row => JsObject(cols.zip(row)))
        case obj: JsObject => values.map { row =>
          if(row.size > 1)
            throw Neo4PlayException(s"Cannot parse multi node RETURN. $q")
          else row.head.as[JsObject]
        }

        case _             => throw Neo4PlayException(s"neo4j exception: Cannot parse result request: $json")
      }
    }
  }
}