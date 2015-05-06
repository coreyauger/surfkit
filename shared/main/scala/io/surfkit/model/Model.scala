package io.surfkit.model


sealed trait Model


case class User(main: ProviderProfile, identities: List[ProviderProfile]) extends  Model
/**
 * A minimal user profile
 */
trait UserProfile {
  def providerId: String
  def userId: String
}

/**
 * A generic profile
 */
trait GenericProfile extends UserProfile {
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

/**
 * The OAuth 1 details
 *
 * @param token the token
 * @param secret the secret
 */
case class OAuth1Info(token: String, secret: String)

/**
 * The Oauth2 details
 *
 * @param accessToken the access token
 * @param tokenType the token type
 * @param expiresIn the number of seconds before the token expires
 * @param refreshToken the refresh token
 */
case class OAuth2Info(accessToken: String, tokenType: Option[String] = None,
                      expiresIn: Option[Int] = None, refreshToken: Option[String] = None)

/**
 * The password details
 *
 * @param hasher the id of the hasher used to hash this password
 * @param password the hashed password
 * @param salt the optional salt used when hashing
 */
case class PasswordInfo(hasher: String, password: String, salt: Option[String] = None)


/**
 * A class representing an authentication method
 */
case class AuthenticationMethod(method: String) {
  /**
   * Returns true if this authentication method equals another. Eg: user.authMethod.is(AuthenticationMethod.OAuth1)
   *
   * @param m An Authentication Method (see constants)
   * @return true if the method matches, false otherwise
   */
  def is(m: AuthenticationMethod): Boolean = this == m
}

/**
 * Authentication methods used by the identity providers
 */
object AuthenticationMethod {
  val OAuth1 = AuthenticationMethod("oauth1")
  val OAuth2 = AuthenticationMethod("oauth2")
  val OpenId = AuthenticationMethod("openId")
  val UserPassword = AuthenticationMethod("userPassword")
}




case class SearchResult(id: String, title:String, description: String ) extends Model

sealed trait Operation
case class SocketOp(slot:String, op:String, data:Model) extends Operation


