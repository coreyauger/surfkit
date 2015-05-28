package io.surfkit.modules

import java.util.{Date, UUID}

import akka.util.Timeout
import core.common.NeoService
import io.surfkit.model.Chat.ChatID
import io.surfkit.model.Auth.ProfileInfo
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

  def createNeo4JConstrains() = {
    implicit val timeout:Timeout = new Timeout(5 seconds)
    Q("CREATE CONSTRAINT ON (c:Chat) ASSERT c.id IS UNIQUE;").run()
    Q("CREATE CONSTRAINT ON (d:Drive) ASSERT d.id IS UNIQUE;").run()
  }
  createNeo4JConstrains()

  //Do we really need all those return projections
  def getMembersDetailsQ(chatId: String) =
    Q(
      """
        |MATCH
        |   (u:User)-[:MEMBER_OF]->(c:Chat {id:{chatId}})
        |MATCH
        |   (u:User)-[:HAS_ACCOUNT]->(p:Provider)
        |RETURN c.id as chatId, u.uid as uid, c.name as group, u.token as token, p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl;
      """
    ).use("chatId" -> chatId)
  def getMembersDetails(chatId: String) = getMembersDetailsQ(chatId).getMany[Auth.ProfileInfo]

  def getMembersQ(chatId: ChatID) =
    Q(
      """
        |MATCH
        |   (u:User)-[:MEMBER_OF]->(c:Chat {id:{chatId}})
        |RETURN u.uid as uid
      """
    ).use("chatId" -> chatId.toString)
  def getMembers(chatId: ChatID) = getMembersQ(chatId).getMultiple[String]("uid")


  def getUnreadChats( uid: String ) =
    Q(
      """
        |MATCH (u:User {uid:{uid}})-[r:UNREAD]->(c:Chat)
        |RETURN c.id AS chatId, TOSTRING(r.count) AS unread;
      """
    ).use( "uid" -> uid).getManyJs

  def getChatsContainingUser( uid: String ): Future[Seq[ChatID]] =
    Q(
      """
        |MATCH (u:User {uid:{uid}})-[:MEMBER_OF]->(c:Chat)
        |RETURN c.id as chatId
      """
    ).use("uid" -> uid).getMultiple[ChatID]("chatId")

  def setChatOrGroupNameQ(id: ChatID, name: String) =
    Q(
      """
       MATCH (c:Chat { id: {id} })
       SET n.name = {name}
       RETURN n
      """
    ).use("id" -> id.toString, "name" -> name)
  def setChatOrGroupName(chatId: ChatID, name: String) = setChatOrGroupNameQ(chatId, name).getOneJs

  def getGroups(userId: String) =
    Q(
      """
        | MATCH
        |   (u:User {uid:{uid}})-[:MEMBER_OF]->(g:Chat {isGroup:true})
        | RETURN g.name as name, g.id as id, g.avatarUrl as avatarUrl;
      """
    ).use("uid" -> userId).getManyJs


  def connectMemberQ(chatId: ChatID, userId: String) =
    Q(
      """
        MATCH (u:User), (c: Chat)
        WHERE u.uid={userId} AND c.id={chatId}
        CREATE UNIQUE (u)-[r:MEMBER_OF]->(c)
        RETURN r
      """
    ).use("chatId" -> chatId.toString, "userId" -> userId)
  def connectMember(chatId: ChatID, userId: String) = connectMemberQ(chatId, userId).getOneJs

  def connectMembersQ(chatId: ChatID, users: Seq[String]) = {
    val usersArr = users.mkString("[", ",", "]")
    Q(
      s"""
        MATCH (u:User), (c: Chat)
        WHERE u.uid IN $usersArr
        AND c.id={chatId}
        CREATE UNIQUE (u)-[r:MEMBER_OF]->(c)
        RETURN r
      """
    ).use("chatId" -> chatId.toString)
  }
  def connectMembers(chatId: ChatID, userId: String) = connectMemberQ(chatId, userId).getOneJs

  def createChatNodeQ(chatId: ChatID, owner: String, members: Set[String]) =
    Q(
      """
        MATCH (m), (u)
        WHERE m.uid IN {members}
        AND u.uid={owner}
        MERGE (c:Chat { id:{chatId} })
        CREATE UNIQUE (u)-[:OWNS]->(c)
        CREATE UNIQUE (m)-[:MEMBER_OF]->(c)
        RETURN c.chatId as chatId
      """
    ).use("chatId" -> chatId.toString, "owner" -> owner, "members" -> members)
  def createChatNode(chatId: ChatID, owner: String, members: Set[String]) = createChatNodeQ(chatId, owner, members).getSingle[String]("chatId")

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



}