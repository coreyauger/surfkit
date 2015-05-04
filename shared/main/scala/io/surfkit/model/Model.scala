package io.surfkit.model

import scala.math._

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




// Some test classes
case class IpInfo(ip: String, country: String, city: String, latitude: Double, longitude: Double)

case class IpPairSummaryRequest(ip1: String, ip2: String)

case class IpPairSummary(distance: Option[Double], ip1Info: IpInfo, ip2Info: IpInfo)

object IpPairSummary {
  def apply(ip1Info: IpInfo, ip2Info: IpInfo): IpPairSummary = IpPairSummary(calculateDistance(ip1Info, ip2Info), ip1Info, ip2Info)

  private def calculateDistance(ip1Info: IpInfo, ip2Info: IpInfo): Option[Double] = {
    (ip1Info.latitude, ip1Info.longitude, ip2Info.latitude, ip2Info.longitude) match {
      case (lat1, lon1, lat2, lon2) =>
        // see http://www.movable-type.co.uk/scripts/latlong.html
        val φ1 = toRadians(lat1)
        val φ2 = toRadians(lat2)
        val Δφ = toRadians(lat2 - lat1)
        val Δλ = toRadians(lon2 - lon1)
        val a = pow(sin(Δφ / 2), 2) + cos(φ1) * cos(φ2) * pow(sin(Δλ / 2), 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        Option(EarthRadius * c)
      case _ => None
    }
  }

  private val EarthRadius = 6371.0
}