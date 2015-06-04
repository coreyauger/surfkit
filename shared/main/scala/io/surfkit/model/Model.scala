package io.surfkit.model

import io.surfkit.model.Auth.UserID

sealed trait Model


// Model for HangTen (Auth) Module
object Auth{

  case class UserID(userId: Long) extends AnyVal {
    override def toString = userId.toString
  }

  case class SurfKitUser(id:Long, token:String, fullName:Option[String], avatarUrl: Option[String], email:Option[String]) extends Model

  case class User(main: SurfKitUser, identities: List[ProviderProfile]) extends Model
  /**
   * A minimal user profile
   */
  sealed trait UserProfile extends Model {
    def providerId: String
    def userId: String
  }

  /**
   * A generic profile
   */
  sealed trait GenericProfile extends UserProfile {
    def firstName: Option[String]
    def lastName: Option[String]
    def fullName: Option[String]
    def email: Option[String]
    def avatarUrl: Option[String]
    def authMethod: AuthenticationMethod
    def oAuth1Info: Option[OAuth1Info]
    def oAuth2Info: Option[OAuth2Info]
    def passwordInfo: Option[PasswordInfo]
  }

  /**
   * An implementation of the GenericProfile
   */
  case class ProviderProfile(
                              id: Long,
                              userKey:Long,
                              appId: String,
                              providerId: String,
                              userId: String,
                              firstName: Option[String],
                              lastName: Option[String],
                              fullName: Option[String],
                              email: Option[String],
                              avatarUrl: Option[String],
                              authMethod: AuthenticationMethod,
                              oAuth1Info: Option[OAuth1Info] = None,
                              oAuth2Info: Option[OAuth2Info] = None,
                              passwordInfo: Option[PasswordInfo] = None) extends GenericProfile


  case class CreateActor(appId: String, userId:Long ) extends Model
  case class AbsorbActorReq(channelId:Api.Route)extends Model
  case class AbsorbActorRes(channels:List[Api.Route])extends Model
  case class Echo(msg:String, users:List[Long]) extends  Model

  case class FindUser(appId: String, providerId:String, userId:String) extends Model
  case class GetFriends(appId: String, userId:Long ) extends Model
  case class SaveResponse(userId: Long) extends Model
  case class OAuth1Info(token: String, secret: String) extends Model
  case class OAuth2Info(accessToken: String, tokenType: Option[String] = None, expiresIn: Option[Int] = None, refreshToken: Option[String] = None) extends  Model
  case class PasswordInfo(hasher: String, password: String, salt: Option[String] = None) extends  Model
  case class AuthenticationMethod(method: String)

  object AuthenticationMethod  extends  Model{
    val OAuth1 = AuthenticationMethod("oauth1")
    val OAuth2 = AuthenticationMethod("oauth2")
    val OpenId = AuthenticationMethod("openId")
    val UserPassword = AuthenticationMethod("userPassword")
  }

  case class ProfileInfo(provider: String, id: String, fullName: String, email: String, jid: String, avatarUrl: String)

}




object Chat {

  case class ChatID(chatId: Long) extends AnyVal {
    override def toString = chatId.toString
  }

  sealed trait ChatMsg extends Model
  case class CreateGroup(name: String, permission: Short, members: List[String]) extends ChatMsg
  case class ChatPresenceRequest(jid: String, status: String) extends ChatMsg
  case class ChatPresenceResponse(jid: String, status: String) extends ChatMsg
  case class GetChatList(uid:UserID, since: String) extends ChatMsg
  case class GetHistory(chatId: ChatID, maxId: Option[Long] = None, offset: Option[Long] = None) extends ChatMsg
  case class GetChat(chatId: ChatID) extends ChatMsg
  case class GetUserGroups() extends ChatMsg
  case class MemberJoin(chatId: ChatID, memberId: String) extends ChatMsg
  case class ChatSend(userId:UserID,
                            chatId: ChatID,
                            author: String,
                            time: Long,
                            msg: String) extends ChatMsg
  case class ChatCreate(userId:UserID,members: Set[String]) extends ChatMsg
  case class SetChatOrGroupName(chatId: ChatID, name: String) extends ChatMsg


  //
  case class DbEntry(chatid:Long, chatentryid:Long, jid:String, timestamp:Long, provider:Short, json:String) extends ChatMsg
  case class ChatEntry(chatid:Long, chatentryid:Long, timestamp:Long, provider:Short, json:String, from:Auth.ProfileInfo) extends ChatMsg
  case class Chat(chatid:Long, members:Seq[Auth.ProfileInfo], entries:Seq[ChatEntry]) extends ChatMsg
}



object Providers {

  sealed trait Provider extends Model{
    def name: String
    def idx: Int
  }
  case object Walkabout extends Provider{
    val name = "walkabout"
    val idx  = 0
  }
  case object Facebook extends Provider{
    val name = "facebook"
    val idx  = 1
  }
  case object Google extends Provider{
    val name = "google"
    val idx  = 2
  }
}




object Socket{
  case class Op(module:String, op:String, data:Model) extends Model
}


object Api {
  case class Route(id: String, reply: String, tag: Long) extends Model
  case class Request(module: String, op: String, data: String, routing: Route)extends Model

  case class Result(status: Int, module: String, op: String, data: String, routing: Route) extends Model

  case class Error(msg:String) extends Model
}



object Max {
  case class Search(category:String, query:String, lat:Double, lng:Double, next: Option[String] = None) extends Model
  case class SearchResult(id:String, api:String, title: String, details: String, highlights: String, url: String, img: String, tags:String, lat:Double = 0.0, lng: Double = 0.0) extends Model
  case class SearchResultList(category: String, next:String, num:Int, pages:Int, results:List[SearchResult]) extends Model
}





