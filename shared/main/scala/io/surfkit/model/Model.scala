package io.surfkit.model

sealed trait Model


// Model for HangTen (Auth) Module
object Auth{

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

object WS{
  case class WebSocketOp(module:String, op:String, data:Model) extends Model
}


object Api {

  case class ApiRoute(id: String, reply: String, tag: Long) extends Model
  case class ApiRequest(module: String, op: String, routing: ApiRoute, data: String) extends Model
  case class ApiResult(module: String, op: String, routing: ApiRoute, data: String) extends Model

}


case class SearchResult(id: String, title:String, description: String ) extends Model


