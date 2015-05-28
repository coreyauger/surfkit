package io.surfkit.modules

import java.util.Date

import io.surfkit.core.common.PostgresService
import io.surfkit.model.Chat.ChatID
import io.surfkit.model._
import org.joda.time.LocalDateTime
import play.api.libs.json._

import scala.concurrent.Future


trait ChatStore extends PostgresService {

  implicit val chatIdRead2: Reads[ChatID] = (JsPath).read[Long].map(ChatID(_))
  implicit val chatIdWrite2: Writes[ChatID] = Writes {
    (chatId: ChatID) => JsNumber(chatId.chatId)
  }


  import PostgresQ._
  //TODO Wait to see if it's used
/*  def getChatById( id: Long, entries: Option[java.util.Date] = None, maxid: Option[Long] = None ):Future[Option[Chat]] = {
    Q("""
      SELECT * FROM public.\"Chat\" c
      WHERE c.chat_id = ?;
      """
    ).use(id)

    None
  }*/


  class ChatEntry( val chatid:ChatID, val chatentryid:Long, val from:String, val timestamp:LocalDateTime, val provider:Short, val json:JsValue) extends DbObject{
    def toJson: JsObject = {
      Json.obj("chatentryid" -> Json.toJson(chatentryid))
    }
    override def toString = Json.stringify(toJson)
  }

  implicit val chatEntryReader = RowReader[ChatEntry]{ row =>
    new ChatEntry(
      ChatID(row("chatentry_chat_key").asInstanceOf[Long]),
      row("chatentry_id").asInstanceOf[Long],
      row("chatentry_from_jid").asInstanceOf[String],
      row("chatentry_timestamp").asInstanceOf[LocalDateTime],
      row("chatentry_provider").asInstanceOf[Short],
      Json.parse( row("chatentry_json").asInstanceOf[String] )
    )
  }


  def createChat(uid: Long, name: Option[String] = None, permission: Option[Short] = None): Future[ChatID] = {
    Q(
      """
        |INSERT INTO public."Chat" (chat_created,chat_updated,chat_creator_key, group_name, group_permission)
        |VALUES (?,?,?,?,?)
        |RETURNING chat_id;
      """
    ).use(dateTimeStr(), dateTimeStr(), uid, name.getOrElse(null), permission.getOrElse(null)).getSingle[ChatID]("chat_id")
  }


  def createGroup(uid: Long, name: String, permission: Short): Future[Long] = {
    Q(
      """
        |INSERT INTO public."Chat" (chat_created,chat_updated,chat_creator_key, group_name, group_permission)
        |VALUES (?,?,?,?,?)
        |RETURNING chat_id;
      """
    ).use(dateTimeStr(), dateTimeStr(), uid, name, permission).getSingle[Long]("chat_id")
  }

  def getChatEntriesByChatId( id: ChatID, offset: Long = 0L, limit: Long = 20L ):Future[Seq[ChatEntry]] = {
    Q(
      """
        |SELECT *
        |FROM public."ChatEntry" CE
        |WHERE CE.chatentry_chat_key = ?
        |ORDER BY CE.chatentry_id DESC
        |LIMIT ? OFFSET ?;
      """
    ).use(id, limit, offset).getRows[ChatEntry]
  }

  def getChatEntriesForChats(chatIds:Seq[ChatID], date: Date, limit: Int = 25):Future[Seq[ChatEntry]] = {
    Q(
      """
        |SELECT DISTINCT ON (ce.chatentry_chat_key) *
        |FROM public."ChatEntry" ce
        |JOIN public."Chat" c ON c.chat_id = ce.chatentry_chat_key
        |WHERE c.chat_id = ANY(?) AND CE.chatentry_timestamp > ?
        |ORDER BY ce.chatentry_chat_key, ce.chatentry_id DESC
        |LIMIT ?;
      """
    ).use(chatIds.toArray, dateTimeStr(date), limit).getRows[ChatEntry]
  }

  def addChatEntry(chatid: ChatID, from: String, provider: Providers.Provider, msg: String):Future[ChatID] = {
    Q(
      """
        |INSERT INTO public."ChatEntry"
        | (chatentry_from_jid, chatentry_timestamp, chatentry_chat_key, chatentry_provider, chatentry_json)
        | VALUES(?,?,?,?,?)
        | RETURNING chatentry_id;
      """
    ).use(from, dateTimeStr(), chatid.chatId, provider.idx, Json.obj("msg" -> msg, "ts" -> dateTimeStr())).getSingle[ChatID]("chatentry_id")
  }



}
