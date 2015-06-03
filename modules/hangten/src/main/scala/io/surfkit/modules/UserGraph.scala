package io.surfkit.modules

import java.util.{Date, UUID}

import akka.util.Timeout
import core.common.NeoService
import play.api.libs.json.{JsValue, JsObject, JsArray, Json}
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import io.surfkit.model._

import scala.util.{Failure, Success}

trait UserGraph extends NeoService {

  implicit def neo4jserver = new Neo4JServer("127.0.0.1", 7474, "/db/data/")
  //We provide xxxQ for the rest of the world so they can make transactions just by apppending Qs

  def createNeo4JConstrains() = {
    implicit val timeout:Timeout = new Timeout(5 seconds)
    Q("CREATE CONSTRAINT ON (u:User) ASSERT u.uid IS UNIQUE;").run()
    Q("CREATE CONSTRAINT ON (u:Drive) ASSERT u.id IS UNIQUE;").run()
    Q("CREATE CONSTRAINT ON (p:Profile) ASSERT p.id IS UNIQUE;").run()
  }
  createNeo4JConstrains()


  import io.surfkit.model.Auth.ProfileInfo
  implicit val profR = Json.reads[Auth.ProfileInfo]
  implicit val profW = Json.writes[Auth.ProfileInfo]

  //We provide xxxQ for the rest of the world so they can make transactions just by apppending Qs


  def getProfileInfo(userId: Long): Future[Seq[Auth.ProfileInfo]] = {
    Q(
      """
         MATCH
           (u)-[:HAS_ACCOUNT]->(p:Provider {jid:{jid}})
         RETURN p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl;
      """
    ).use("jid" -> userId).getMany[Auth.ProfileInfo]
  }

  def getFriends(userId: Long): Future[JsArray] = {
    Q(
      """
        | MATCH
        |   (u {uid:{uid}})-[:HAS_ACCOUNT]->(up:Provider)-[:FRIEND]->(p)
        | RETURN p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl;
      """
    ).use( "uid" -> userId).getManyJs
  }


  def connectFriendQ(provider: String, uid: Long, friendUuid: Long) =
    Q(
      """
        |MATCH (f:User {uid:{friendUuid}})-[:HAS_ACCOUNT]->(fp:Provider {name:{provider}}),
        |      (u:User {uid:{uid}})-[:HAS_ACCOUNT]->(p:Provider {name:{provider}})
        |CREATE UNIQUE
        | (fp)-[:FRIEND]->(p)-[:FRIEND]->(fp)
        |RETURN fp;
      """
    ).use("uid" -> uid, "friendUuid" -> friendUuid, "provider" -> provider)


  def createAndConnectDeviceQ(uid: Long, deviceID: String, deviceType: String) =
    Q(
      """
        MATCH (u: User)
        WHERE u.uid={uid}
        MERGE (d:Device {id: {deviceID}, deviceType: {deviceType} })
        CREATE UNIQUE (u)-[r:NOTIFY_ON]->(d)
        RETURN r
      """
    ).use("uid" -> uid, "deviceID" -> deviceID, "deviceType" -> deviceType)


  def addFriend(uid: Long, friendId: Long) = ???


  def getProvidersQ(uid: Long) = Q("MATCH (u:User)-[:MANAGES]->(:Profile)->[:HAS_ACCOUNT]->(p:Provider) WHERE u.uid = {uid} RETURN p").use("uid" -> uid)
  def getProviders(uid: Long): Future[JsArray] = getProvidersQ(uid).getManyJs


  def mergeFriends(uid: Long, friends: Seq[JsObject], provider: String) = {
    println(s"PROPS: $friends, provider: $provider")
    Q("""
        MATCH (u: User)-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(pu:Provider)
        WHERE u.uid = {uid} AND pu.name = {provider}
        FOREACH (p in {props} |
          MERGE (f:Provider {name: p.name, jid: p.jid, id: p.id})
          CREATE UNIQUE (pu)-[:FRIEND]->(f)-[:FRIEND]->(pu))
        RETURN u
      """).use("uid" -> uid, "provider" -> provider, "props" -> friends).run()
    }

  def addAppFriends(appId: String, uid: Long, jids: Seq[String]) = {
    println(s"$appId, $uid, $jids")
    Q(
      """
        |MATCH (u1:User {uid: {uid}})-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(w1:Provider {name:{appId}}),
        |(friend:User)-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(w2:Provider {name:{appId}})
        |MATCH (friend:User)-[:MANAGES]->(:Profile)-[:HAS_ACCOUNT]->(p:Provider)
        |WHERE p.jid in {jids}
        |CREATE UNIQUE
        |(w1)-[:FRIEND]->(w2)-[:FRIEND]->(w1)
        |RETURN w2;
      """).use("appId" -> appId, "uid" -> uid, "jids" -> jids).
      getManyJs
    }



  def userProviderToJid(provider:String, user:Auth.ProviderProfile) = {
    provider match{
      case "facebook" => "%s@chat.facebook.com".format(user.userId)
      case "google" =>  user.email.get
      case "twitter" => "%s@twitter.com".format(user.userId)
      case "linkedin" => "%s@linkedin.com".format(user.userId)
      case "instagram" => "%s@instagram.com".format(user.userId)
      case "userpass" => user.email.get
    }
  }

  def saveUserGraph(uid:Long, user:Auth.ProviderProfile) = {

    val oauth2: Auth.OAuth2Info = user.oAuth2Info.getOrElse(Auth.OAuth2Info(""))
    val oauth1: Auth.OAuth1Info = user.oAuth1Info.getOrElse(Auth.OAuth1Info("", ""))
    val pinfo : Auth.PasswordInfo =  user.passwordInfo.getOrElse(Auth.PasswordInfo("","",Some("")) )
    val jid:String = userProviderToJid(user.providerId, user)

    Q("MATCH (u:User {isPseudo: false})-[:MANAGES]->(r:Profile)-[:HAS_ACCOUNT]->(p:Provider) WHERE p.name = {provider} AND p.id = {userid} RETURN p.id as providerid, u.uid as uid;", Json.obj("userid"->user.userId,"provider"->user.providerId)).getOneOptJs.map {
      case provider =>
        val uParams = Json.obj("providerId" -> user.providerId, "userId" -> user.userId, "firstName" -> user.firstName.getOrElse[String](""), "lastName" -> user.lastName.getOrElse[String](""),
          "fullName" -> user.fullName.getOrElse[String](""), "authMethod" -> user.authMethod.method, "OAuth1InfoToken" -> oauth1.token, "OAuth1InfoSecret" -> oauth1.secret,
          "accessToken" -> oauth2.accessToken, "expiresIn" -> oauth2.expiresIn.getOrElse[Int](0), "refreshToken" -> oauth2.refreshToken.getOrElse[String](""), "tokenType" -> oauth2.tokenType.getOrElse[String](""),
          "avatarUrl" -> user.avatarUrl.getOrElse[String](""), "email" -> user.email.getOrElse[String](""), "hasher" -> pinfo.hasher, "password" -> pinfo.password, "salt" -> pinfo.salt.getOrElse[String](""))
        if (provider == None) {
          // no provider of this type
          println("CREATE USER")
          val backupEmail = s"${user.userId}@chat.${user.providerId}.com"
          val token = UUID.randomUUID.toString.replace("-", "")
          val waJid = s"$uid@${user.appId}"
          val profile = s"u${uid}-${user.appId}-default"
          val name = user.firstName.getOrElse("") + " " + user.lastName.getOrElse("")
          val avatarUrl = user.avatarUrl.getOrElse("")
          val params = uParams ++ Json.obj("appId" -> user.appId, "profile" -> profile, "waJid" -> waJid, "jid" -> jid, "uid" -> uid)
          // fire and forget...
          Q( """
                |MERGE (p:Provider {jid:{jid}})
                |SET
                |p.name = {providerId},
                |p.id = {userId},
                |p.jid = {jid},
                |p.firstName = {firstName},
                |p.lastName = {lastName},
                |p.fullName = {fullName},
                |p.authMethod = {authMethod},
                |p.oAuth1InfoToken = {OAuth1InfoToken},
                |p.oAuth1InfoSecret = {OAuth1InfoSecret},
                |p.oAuth2InfoAccessToken = {accessToken},
                |p.oAuth2InfoExpiresIn = {expiresIn},
                |p.oAuth2InfoRefreshToken = {refreshToken},
                |p.oAuth2InfoTokenType = {tokenType},
                |p.avatarUrl = {avatarUrl},
                |p.email = {email},
                |p.hasher = {hasher},
                |p.password = {password},
                |p.salt = {salt};
              """.stripMargin,params).run
            .map{
              case j2 =>
                Q( """MATCH (p:Provider {jid:{jid}})
                    |CREATE UNIQUE
                    |(p)<-[:HAS_ACCOUNT]-(r:Profile
                    |{
                    | id: {profile},
                    | type: 'default',
                    | cover: '',
                    | uid : {uid},
                    | token : {token},
                    | name: {name},
                    | avatarUrl: {avatarUrl}
                    |}
                    |)<-[:MANAGES]-(u:User
                    |{
                    | uid : {uid},
                    | isPseudo: false,
                    | token : {token},
                    | name: {name},
                    | avatarUrl: {avatarUrl}
                    | }
                    |);
                  """.stripMargin,Json.obj("profile" -> profile, "jid" ->jid, "uid"->uid,"token"->token,"name"->name,"avatarUrl" -> avatarUrl)).run.map {
                    case userNeoJson =>
                    if( user.email != None) {
                      Q(
                        """
                          |MATCH (p:Profile {id:{profile}})-[:HAS_ACCOUNT]->(p:Provider {name:'email',jid:{email}})
                          |WITH COUNT(u) as exists
                          |WHERE exists = 0
                          |MATCH (r:Profile {id: {profile}})
                          |CREATE UNIQUE
                          |(r)-[:HAS_ACCOUNT]->(p:Provider
                          |{
                          |name:'email',
                          |id: {email},
                          |jid: {email},
                          |firstName: {firstName},
                          |lastName: {lastName},
                          |fullName:{fullName},
                          |authMethod: '',
                          |oAuth1InfoToken : '',
                          |oAuth1InfoSecret : '',
                          |oAuth2InfoAccessToken : '',
                          |oAuth2InfoExpiresIn : 0,
                          |oAuth2InfoRefreshToken : '',
                          |oAuth2InfoTokenType : '',
                          |avatarUrl : {avatarUrl},
                          |email : '',
                          |hasher : '',
                          |password : '',
                          |salt : ''
                          |}
                          |);
                          |
                        """.stripMargin,params).run
                    }
                    Q( """
                        |MATCH (u:User {uid: {uid}})-[:MANAGES]->(r:Profile {id:{profile}})
                        |CREATE UNIQUE (u)-[:OWNS]->(d:Drive {id:{uid}})
                        |CREATE UNIQUE (r)-[:OWNS]->(d2:Drive {id:{profile}});
                      """.stripMargin).use("profile" -> profile, "uid"-> uid).run.map {
                      case done =>
                        Q(
                          """
                            | MATCH (r:Profile {id: {profile}})
                            | CREATE UNIQUE (r)-[:HAS_ACCOUNT]->(p:Provider
                            | {
                            |  name: {appId},
                            |  id : STR({uid}),
                            |  jid: {waJid},
                            |  firstName: {firstName},
                            |  lastName: {lastName},
                            |  fullName: {fullName},
                            |  authMethod: "",
                            |  oAuth1InfoToken: "",
                            |  oAuth1InfoSecret: "",
                            |  oAuth2InfoAccessToken: "",
                            |  oAuth2InfoExpiresIn: 0,
                            |  oAuth2InfoRefreshToken: "",
                            |  oAuth2InfoTokenType: "",
                            |  avatarUrl: {avatarUrl},
                            |  email: {email},
                            |  hasher: "",
                            |  password: "",
                            |  salt: ""
                            |  }
                            |);
                          """.
                          stripMargin,params).run.onComplete{
                            case _ =>
                              // (CA) now that the user node exists and WA node exists.. lets link accounts
                              //WalkaboutAPI.linkJidToUser(uid, jid)
                              //WalkaboutAPI.linkJidToUser(uid, waJid)
                              println(done)
                              println("DONE...")
                        }
                    }
                }
            }

        }else{
            println("Already a provider.. doing update on tokens")

            val waJid = s"$uid@${user.appId}"
            val params = uParams ++ Json.obj("waJid"-> waJid, "uid" -> uid, "jid" -> jid)
            // we have a provider... but need to update the tokens
            Q("""
                |MATCH (p:Provider { name: {providerId}, id: {userId} })
                |SET
                |p.jid = {jid},
                |p.firstName = {firstName},
                |p.lastName = {lastName},
                |p.fullName = {fullName},
                |p.authMethod = {authMethod},
                |p.oAuth1InfoToken = {OAuth1InfoToken},
                |p.oAuth1InfoSecret = {OAuth1InfoSecret},
                |p.oAuth2InfoAccessToken = {accessToken},
                |p.oAuth2InfoExpiresIn = {expiresIn},
                |p.oAuth2InfoRefreshToken = {refreshToken},
                |p.oAuth2InfoTokenType = {tokenType},
                |p.avatarUrl = {avatarUrl},
                |p.email = {email},
                |p.hasher = {hasher},
                |p.password = {password},
                |p.salt = {salt},
                |RETURN p;
              """.stripMargin, params).run.onComplete{
              case _ =>
                println("DONE...")
            }
        }
    }


  }

  def getUserFriendsQ(uid: Long, nodeId:Long = -1) = Q(s"""
          | MATCH (u:User {uid:{uid}})-[:MANAGES]->(r:Profile)-[:HAS_ACCOUNT]->(up:Provider)-[:FRIEND]->(p)
          | WHERE ID(p) > ${nodeId}
          | RETURN p.name as provider, p.id as id, p.fullName as fullName, p.email as email, p.jid as jid, p.avatarUrl as avatarUrl, str(ID(p)) as node;
        """.stripMargin).use("uid" -> uid)
  def getUserFriends(uid:Long, node:Long = -1) = getUserFriendsQ(uid, node).getMany[Auth.ProfileInfo]


  def addFriendsForUser(appId:String, uid:Long, provider:String, roster:List[Auth.ProfileInfo]) = {
    val t1 = new Date().getTime()
    getUserFriends(uid, 0).onSuccess {
      case friends:List[Auth.ProfileInfo] =>
        val friendMap = friends.map(f => (f.jid, f)).toMap
        val toAdd: List[JsObject] = roster.filterNot(f => friendMap.contains(f.jid)).map(c =>Json.obj("name" -> provider, "jid" -> c.jid, "id" -> c.id.toString, "fullName" -> c.fullName, "avatarUrl" -> c.avatarUrl, "email" -> c.email))
        mergeFriends(uid, toAdd, provider).onComplete{
          case Failure(e) => println(e)
          case Success(ret) =>
          println(s"RET: $ret")
          addAppFriends(appId, uid, toAdd.map(f => (f \ "jid").as[String])).onFailure { case e =>
            e.printStackTrace()
          }
          println("set roster took " + (new Date().getTime() - t1) + " ms for " + toAdd.size + " friends")
        }
    }
  }


  def addFriendsToGraph(appId:String, uid:Long, user:Auth.ProviderProfile) =
    user.providerId match{
      case "facebook" =>
        println(s"https://graph.facebook.com/v2.3/me/friends?access_token=${user.oAuth2Info.map(o => o.accessToken).get}")
        ws.url(s"https://graph.facebook.com/v2.3/me/friends?access_token=${user.oAuth2Info.map(o => o.accessToken).getOrElse("")}").get().map{
        resp =>
          val flist = (resp.json \ "data").as[List[JsValue]]
          addFriendsForUser(appId, uid, "facebook", flist.map(f => Auth.ProfileInfo("facebook", (f \ "id").as[String], (f \ "name").as[String], "", s"${(f \ "id").as[String]}@chat.facebook.com", s"http://graph.facebook.com/${(f \ "id").as[String]}/picture?type=square")))
      }
    }


}