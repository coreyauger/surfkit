package io.surfkit.modules

import java.util.Date

import io.surfkit.core.common.PostgresService
import io.surfkit.model.Chat.{ChatEntry, ChatID}
import io.surfkit.model._
import org.joda.time.LocalDateTime
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits.global

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


  def createSchemaQ() =
    Q(
      """
        |--
        |-- Name: Chat; Type: TABLE; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |CREATE TABLE "Chat" (
        |    chat_id bigint NOT NULL,
        |    chat_created timestamp without time zone NOT NULL,
        |    chat_updated timestamp without time zone NOT NULL,
        |    chat_creator_key bigint NOT NULL,
        |    group_name character varying(255),
        |    group_permission smallint DEFAULT 0
        |);
        |
        |
        |ALTER TABLE public."Chat" OWNER TO postgres;
        |
        |--
        |-- Name: ChatEntry; Type: TABLE; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |CREATE TABLE "ChatEntry" (
        |    chatentry_id bigint NOT NULL,
        |    chatentry_from_jid character varying(256) NOT NULL,
        |    chatentry_json json NOT NULL,
        |    chatentry_chat_key bigint NOT NULL,
        |    chatentry_provider smallint NOT NULL,
        |    chatentry_timestamp timestamp without time zone NOT NULL
        |);
        |
        |
        |ALTER TABLE public."ChatEntry" OWNER TO postgres;
        |
        |--
        |-- Name: ChatEntry_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
        |--
        |
        |CREATE SEQUENCE "ChatEntry_id_seq"
        |    START WITH 1
        |    INCREMENT BY 1
        |    NO MINVALUE
        |    NO MAXVALUE
        |    CACHE 1;
        |
        |
        |ALTER TABLE public."ChatEntry_id_seq" OWNER TO postgres;
        |
        |--
        |-- Name: ChatEntry_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
        |--
        |
        |ALTER SEQUENCE "ChatEntry_id_seq" OWNED BY "ChatEntry".chatentry_id;
        |
        |
        |--
        |-- Name: Chat_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
        |--
        |
        |CREATE SEQUENCE "Chat_id_seq"
        |    START WITH 1
        |    INCREMENT BY 1
        |    NO MINVALUE
        |    NO MAXVALUE
        |    CACHE 1;
        |
        |
        |ALTER TABLE public."Chat_id_seq" OWNER TO postgres;
        |
        |--
        |-- Name: Chat_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
        |--
        |
        |ALTER SEQUENCE "Chat_id_seq" OWNED BY "Chat".chat_id;
        |
        |
        |--
        |-- Name: chat_id; Type: DEFAULT; Schema: public; Owner: postgres
        |--
        |
        |ALTER TABLE ONLY "Chat" ALTER COLUMN chat_id SET DEFAULT nextval('"Chat_id_seq"'::regclass);
        |
        |
        |--
        |-- Name: chatentry_id; Type: DEFAULT; Schema: public; Owner: postgres
        |--
        |
        |ALTER TABLE ONLY "ChatEntry" ALTER COLUMN chatentry_id SET DEFAULT nextval('"ChatEntry_id_seq"'::regclass);
        |
        |--
        |-- Name: ChatEntry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |ALTER TABLE ONLY "ChatEntry"
        |    ADD CONSTRAINT "ChatEntry_pkey" PRIMARY KEY (chatentry_id);
        |
        |
        |--
        |-- Name: Chat_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |ALTER TABLE ONLY "Chat"
        |    ADD CONSTRAINT "Chat_pkey" PRIMARY KEY (chat_id);
        |
        |--
        |-- Name: ChatEntry_from_jid_idx; Type: INDEX; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |CREATE INDEX "ChatEntry_from_jid_idx" ON "ChatEntry" USING btree (chatentry_from_jid);
        |
        |--
        |-- Name: fki_Chat_creator_key_fkey; Type: INDEX; Schema: public; Owner: postgres; Tablespace:
        |--
        |
        |CREATE INDEX "fki_Chat_creator_key_fkey" ON "Chat" USING btree (chat_creator_key);
        |
        |--
        |-- Name: ChatEntry_chat_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
        |--
        |
        |ALTER TABLE ONLY "ChatEntry"
        |    ADD CONSTRAINT "ChatEntry_chat_key_fkey" FOREIGN KEY (chatentry_chat_key) REFERENCES "Chat"(chat_id);
        |
        |
        |--
        |-- Name: Chat_creator_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
        |--
        |
        |ALTER TABLE ONLY "Chat"
        |    ADD CONSTRAINT "Chat_creator_key_fkey" FOREIGN KEY (chat_creator_key) REFERENCES "Users"(user_id);
        |
        |
      """.stripMargin
    ).sendCreateSchemaQuery

  createSchemaQ()




  implicit val chatEntryReader = RowReader[Chat.ChatEntry]{ row =>
    Chat.ChatEntry(
      row("chatentry_chat_key").asInstanceOf[Long],
      row("chatentry_id").asInstanceOf[Long],
      row("chatentry_from_jid").asInstanceOf[String],
      row("chatentry_timestamp").asInstanceOf[LocalDateTime].toDate.getTime,
      row("chatentry_provider").asInstanceOf[Short],
      row("chatentry_json").asInstanceOf[String]
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

  def getChatEntriesByChatId( id: ChatID, offset: Long = 0L, limit: Long = 20L ):Future[Seq[Chat.ChatEntry]] = {
    Q(
      """
        |SELECT *
        |FROM public."ChatEntry" CE
        |WHERE CE.chatentry_chat_key = ?
        |ORDER BY CE.chatentry_id DESC
        |LIMIT ? OFFSET ?;
      """
    ).use(id, limit, offset).getRows[Chat.ChatEntry]
  }

  def getChatEntriesForChats(chatIds:Seq[ChatID], date: Date, limit: Int = 25):Future[Seq[Chat.ChatEntry]] = {
    Q(
      """
        |SELECT DISTINCT ON (ce.chatentry_chat_key) *
        |FROM public."ChatEntry" ce
        |JOIN public."Chat" c ON c.chat_id = ce.chatentry_chat_key
        |WHERE c.chat_id = ANY(?) AND CE.chatentry_timestamp > ?
        |ORDER BY ce.chatentry_chat_key, ce.chatentry_id DESC
        |LIMIT ?;
      """
    ).use(chatIds.toArray, dateTimeStr(date), limit).getRows[Chat.ChatEntry]
  }

  def addChatEntry(chatid: ChatID, from: String, provider: Providers.Provider, msg: String):Future[ChatEntry] = {
    val now = new Date()
    val nowStr = dateTimeStr(now)
    Q(
      """
        |INSERT INTO public."ChatEntry"
        | (chatentry_from_jid, chatentry_timestamp, chatentry_chat_key, chatentry_provider, chatentry_json)
        | VALUES(?,?,?,?,?)
        | RETURNING chatentry_id;
      """
    ).use(from, nowStr, chatid.chatId, provider.idx, Json.obj("msg" -> msg, "ts" -> nowStr)).getSingle[Long]("chatentry_id").map{
      entryId =>
        ChatEntry(chatid.chatId, entryId, from, now.getTime, provider.idx.asInstanceOf[Short], Json.obj("msg" -> msg, "ts" -> dateTimeStr()).toString)
    }
  }



}
