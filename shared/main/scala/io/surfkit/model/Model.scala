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





