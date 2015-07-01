package io.surfkit.core.common

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import com.github.mauricio.async.db._
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.typesafe.config.ConfigFactory

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._

import scala.annotation.implicitNotFound
import scala.concurrent.Future

trait PostgresService {

  val dateFormatUtc: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  dateFormatUtc.setTimeZone(TimeZone.getTimeZone("UTC"))

  def strToDate(s:String) = dateFormatUtc.parse(s)

  val dbConfig = ConfigFactory.load()
  val connectionString = s"${dbConfig.getString("database.jdbc.connection")}?user=${dbConfig.getString("database.jdbc.user")}&password=${dbConfig.getString("database.jdbc.pass")}"
  println(connectionString)
  private val configuration = URLParser.parse(connectionString)
  private val factory = new PostgreSQLConnectionFactory( configuration )

  @implicitNotFound(
    "No Row deserializer found for type ${T}. Try to implement an implicit ResultRead for this type."
  )
  trait RowReader[T] {
    def reads(row: RowData): T
  }

  object RowReader {
    def apply[T](f: RowData => T) = new RowReader[T] {
      def reads(row: RowData) = f(row)
    }
  }

  def dateTimeStr( date: Date = new Date() ) = dateFormatUtc.format(date)

  val roles = Array(
    "Owners",
    "Administrators",
    "Developers",
    "Analytics",
    "Testers"
  )
  def roleToSmallInt( role: String ) = {
    roles.indexOf(role)
  }
  def intToRole(ind: Int) = {
    try {
      roles(ind)
    }catch{
      case _:Throwable =>
        print(s"Unknown role for $ind")
        ""
    }
  }

  object PostgresQ {
    case class Q(q: String, params: Array[Any] = Array()) {

      def use(params: Any*): Q = Q(q, params.toArray)

      def getSingle[T](column: String)(implicit reader: Reads[T]) = postgresJson.map( l => (l.head \ column).as[T])

      def getOneJs() = postgresJson().map(_.head)

      def andThen[T](pf: PartialFunction[QueryResult,T]) = postgresExec(pf)

      def getOne[T](implicit reader: Reads[T]) = postgresJson().map(_.head.as[T])

      def getManyJs(): Future[JsArray] = postgresJson().map(JsArray)

      def getMany[T](implicit reader: Reads[T]) = postgresJson.map(l => l.map(_.as[T]))

      def getRow[T](implicit reader: RowReader[T]): Future[T] = postgresRow.map(l => reader.reads(l.head))

      def getRows[T](implicit reader: RowReader[T]): Future[Seq[T]] = postgresRow.map(l => l.map(reader.reads))

      def sendCreateSchemaQuery() = {
        val conn = factory.create
        conn.sendQuery(q.stripMargin)
      }
      private def postgresExec[T](transformer: (QueryResult) => T): Future[T] = {
        val conn = factory.create
        conn.sendPreparedStatement(q.stripMargin, params).map { (r) =>
          conn.disconnect
          transformer(r)
        }
      }

      private def postgresRow(): Future[Seq[RowData]] = {
        postgresExec(_.rows.toSeq.flatten)
      }

      private def postgresJson(): Future[Seq[JsObject]] = {
        postgresExec(resultsToJson(_))
      }

      private def resultsToJson(results: QueryResult) = {
        val rows = results.rows.toSeq.flatten
        rows.map { row =>
          results.rows.get.columnNames.zip(row).foldLeft(Json.obj()){ (acc, curr) =>
            acc ++ Json.obj(curr._1 -> anyJs(curr._2))
          }
        }
      }

      private def anyJs(anything: Any) = anything match {
        case l:Long     => JsNumber(l)
        case b: Boolean => JsBoolean(b)
        case x          => JsString(x.toString)
      }

    }

  }
}
