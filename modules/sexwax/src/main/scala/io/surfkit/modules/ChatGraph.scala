package io.surfkit.modules

import java.util.{Date, UUID}

import akka.util.Timeout
import core.common.NeoService
import io.surfkit.model.Chat.{ChatMember, ChatID}
import io.surfkit.model.Auth.{UserID, ProfileInfo}
import play.api.libs.json._
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import io.surfkit.model._


trait ChatGraph extends NeoService {

  implicit def neo4jserver = new Neo4JServer("127.0.0.1", 7474, "/db/data/")
  //We provide xxxQ for the rest of the world so they can make transactions just by apppending Qs

  implicit val chatIdRead: Reads[ChatID] = (JsPath).read[Long].map(ChatID(_))
  implicit val chatIdWrite: Writes[ChatID] = Writes {
    (chatId: ChatID) => JsNumber(chatId.chatId)
  }


  implicit val pr =  Json.reads[ProfileInfo]
  implicit val cmr =  Json.reads[ChatMember]

  def createNeo4JConstrains() = {
    implicit val timeout:Timeout = new Timeout(5 seconds)
    Q("CREATE CONSTRAINT ON (c:Chat) ASSERT c.id IS UNIQUE;").run()
    Q("CREATE CONSTRAINT ON (d:Drive) ASSERT d.id IS UNIQUE;").run()
  }
  createNeo4JConstrains()

  //Do we really need all those return projections
  def getMembersDetailsQ(chatId: ChatID) =
    Q(
      """
        |MATCH
        |   (p:Provider)-[:MEMBER_OF]->(c:Chat {id:{chatId}})
        |RETURN p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl;
      """
    ).use("chatId" -> chatId.chatId)
  def getMembersDetails(chatId: ChatID) = getMembersDetailsQ(chatId).getMany[Auth.ProfileInfo]

  def getMembersQ(chatId: ChatID) =
    Q(
      """
        |MATCH
        |   (u:User)-[:MEMBER_OF]->(c:Chat {id:{chatId}})
        |RETURN u.uid as uid
      """
    ).use("chatId" -> chatId.toString)
  def getMembers(chatId: ChatID) = getMembersQ(chatId).getMultiple[String]("uid")


  def getUnreadChats( uid: Long ) =
    Q(
      """
        |MATCH (u:User {uid:{uid}})-[r:UNREAD]->(c:Chat)
        |RETURN c.id AS chatId, TOSTRING(r.count) AS unread;
      """
    ).use( "uid" -> uid).getManyJs

  def getChatMembersContainingUser( uid: Long ): Future[Seq[Chat.ChatMember]] =
    Q(
      """
        |MATCH (:User {uid:{uid}})-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(:Provider)-[:MEMBER_OF]->(c:Chat)
        |MATCH (p:Provider)-[:MEMBER_OF]->(c:Chat)
        |RETURN c.id as chatId,  p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl;
      """
    ).use("uid" -> uid).getMany[Chat.ChatMember]



  def getChatsContainingUser( uid: Long ): Future[Seq[Chat.ChatID]] =
    Q(
      """
        |MATCH (:User {uid:{uid}})-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(:Provider)-[:MEMBER_OF]->(c:Chat)
        |RETURN c.id as chatId;
      """
    ).use("uid" -> uid).getMultiple[Chat.ChatID]("chatId")

  def setChatOrGroupNameQ(id: ChatID, name: String) =
    Q(
      """
       MATCH (c:Chat { id: {id} })
       SET n.name = {name}
       RETURN n
      """
    ).use("id" -> id.toString, "name" -> name)
  def setChatOrGroupName(chatId: ChatID, name: String) = setChatOrGroupNameQ(chatId, name).getOneJs

  def getGroups(userId: Long) =
    Q(
      """
        | MATCH
        |   (u:User {uid:{uid}})-[:MEMBER_OF]->(g:Chat {isGroup:true})
        | RETURN g.name as name, g.id as id, g.avatarUrl as avatarUrl;
      """
    ).use("uid" -> userId).getManyJs


  def connectMemberQ(chatId: ChatID, jid: String) =
    Q(
      """
        MATCH (p:Provider), (c: Chat)
        WHERE p.jid={jid} AND c.id={chatId}
        CREATE UNIQUE (u)-[r:MEMBER_OF]->(c)
        RETURN r
      """
    ).use("chatId" -> chatId, "jid" -> jid)
  def connectMember(chatId: ChatID, jid: String) = connectMemberQ(chatId, jid).getOneJs

  def connectMembersQ(chatId: ChatID, jids: Seq[String]) = {
    val jidsStr = jids.mkString("[", ",", "]")
    Q(
      s"""
        MATCH (p:Provider), (c: Chat)
        WHERE p.jid IN $jidsStr
        AND c.id={chatId}
        CREATE UNIQUE (p)-[r:MEMBER_OF]->(c)
        RETURN r
      """
    ).use("chatId" -> chatId.toString)
  }
  def connectMembers(chatId: ChatID, jids: Seq[String]) = connectMembersQ(chatId, jids).getOneJs

  def createChatNodeQ(chatId: ChatID, owner: UserID, members: Set[String]) =
    Q(
      """
        MATCH (m:Provider), (u:User)
        WHERE m.jid IN {members}
        AND u.uid={owner}
        MERGE (c:Chat { id:{chatId} })
        CREATE UNIQUE (u)-[:OWNS]->(c)
        CREATE UNIQUE (m)-[:MEMBER_OF]->(c)
        RETURN c.id as chatId
      """
    ).use("chatId" -> chatId.chatId, "owner" -> owner.userId, "members" -> members)
  def createChatNode(chatId: ChatID, owner: UserID, members: Set[String]) = createChatNodeQ(chatId, owner, members).getSingle[Long]("chatId")

  def getOwnerQ(chatId: ChatID) =
    Q("""
        MATCH (u:User)-[:OWNS]->(c:Chat)
        WHERE c.id={chatId}
        RETURN u.uid as uid
      """).use("chatId" -> chatId.toString)
  def getOwner(chatId: ChatID) = getOwnerQ(chatId).getSingle[String]("uid")

  def find2WayChat(author: String, recipient: String): Future[Seq[String]] =
    Q("""
        MATCH (a)-[:MEMBER_OF]->(c:Chat), (r)-[:MEMBER_OF]->(c:Chat)
        MATCH (u)-[m:MEMBER_OF]->(c:Chat)
        WHERE a.uid={author} AND r.uid={recipient}
        WITH c.id  as chatId, count(DISTINCT m) as nbR
        WHERE nbR = 2
        RETURN chatId
      """).use("author" -> author, "recipient" -> recipient).getMultiple[String]("chatId")



  def findChat(members: Set[String]): Future[Seq[Long]] =
    Q( s"""
        MATCH ${members.map(jid => s"(:Provider {jid:'$jid'})-[:MEMBER_OF]->(c:Chat)").mkString("", ",", "")}
        MATCH (u:Provider)-[m:MEMBER_OF]->(c:Chat)
        WITH c.id as chatId, count(DISTINCT m) as nbR
        WHERE nbR = ${members.size}
        RETURN chatId
      """).use("members" -> members).getMultiple[Long]("chatId")





}