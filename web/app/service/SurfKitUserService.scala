package service

/**
 * Created by suroot on 04/05/15.
 */

import play.api.Logger
import securesocial.core._
import securesocial.core.providers.{ UsernamePasswordProvider, MailToken }
import scala.concurrent.Future
import securesocial.core.services.{ UserService, SaveMode }

import io.surfkit.model.User
import io.surfkit.model.ProviderProfile

object ProfileImplicits {
  implicit def BasicProfile2ProviderProfile(u : BasicProfile):ProviderProfile =
    ProviderProfile(u.providerId,
      u.userId,
      u.firstName,
      u.lastName,
      u.fullName,
      u.email,
      u.avatarUrl,
      io.surfkit.model.AuthenticationMethod(u.authMethod.method),
      u.oAuth1Info.map( o => io.surfkit.model.OAuth1Info(o.token, o.secret)),
      u.oAuth2Info.map( o => io.surfkit.model.OAuth2Info(o.accessToken, o.tokenType, o.expiresIn, o.refreshToken) ),
      u.passwordInfo.map( p => io.surfkit.model.PasswordInfo(p.hasher, p.password, p.salt) )
    )

  implicit def ProviderProfile2BasicProfile(u : ProviderProfile):BasicProfile =
    BasicProfile(u.providerId,
      u.userId,
      u.firstName,
      u.lastName,
      u.fullName,
      u.email,
      u.avatarUrl,
      AuthenticationMethod(u.authMethod.method),
      u.oAuth1Info.map( o => OAuth1Info(o.token, o.secret)),
      u.oAuth2Info.map( o => OAuth2Info(o.accessToken, o.tokenType, o.expiresIn, o.refreshToken) ),
      u.passwordInfo.map( p => PasswordInfo(p.hasher, p.password, p.salt) )
    )

  implicit def ProviderProfile2BasicProfile(u : Option[ProviderProfile]):Option[BasicProfile] =
    u.map( p => ProviderProfile2BasicProfile(p) )


  implicit def PasswordInfo2SkPassworInfo(p: PasswordInfo): io.surfkit.model.PasswordInfo =
    PasswordInfo(p.hasher, p.password, p.salt)

  implicit def SkPassworInfo2PasswordInfo(p: io.surfkit.model.PasswordInfo):PasswordInfo  =
    PasswordInfo(p.hasher, p.password, p.salt)
}



/**
 * A Sample In Memory user service in Scala
 *
 * IMPORTANT: This is just a sample and not suitable for a production environment since
 * it stores everything in memory.
 */
class SurfKitUserService extends UserService[User] {
  val logger = Logger("application.controllers.SurfKitUserService")

  //
  var users = Map[(String, String), User]()
  //private var identities = Map[String, BasicProfile]()
  private var tokens = Map[String, MailToken]()

  def find(providerId: String, userId: String): Future[Option[BasicProfile]] = {
    import ProfileImplicits._
    if (logger.isDebugEnabled) {
      logger.debug("users = %s".format(users))
    }
    val result = for (
      user <- users.values;
      basicProfile <- user.identities.find(su => su.providerId == providerId && su.userId == userId)
    ) yield {
      basicProfile
    }
    Future.successful(result.headOption)
  }

  def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] = {
    import ProfileImplicits._
    if (logger.isDebugEnabled) {
      logger.debug("users = %s".format(users))
    }
    val someEmail = Some(email)
    val result = for (
      user <- users.values;
      basicProfile <- user.identities.find(su => su.providerId == providerId && su.email == someEmail)
    ) yield {
      basicProfile
    }
    Future.successful(result.headOption)
  }

  private def findProfile(p: BasicProfile) = {
    users.find {
      case (key, value) if value.identities.exists(su => su.providerId == p.providerId && su.userId == p.userId) => true
      case _ => false
    }
  }

  def save(user: BasicProfile, mode: SaveMode): Future[User] = {
    val convert = ProfileImplicits.BasicProfile2ProviderProfile(user)
    mode match {
      case SaveMode.SignUp =>
        val newUser = User(convert, List(convert))
        users = users + ((user.providerId, user.userId) -> newUser)
        Future.successful(newUser)
      case SaveMode.LoggedIn =>
        // first see if there is a user with this BasicProfile already.
        findProfile(user) match {
          case Some(existingUser) =>
            // TODO: ..
            //updateProfile(user, existingUser)
            val newUser = User(convert, List(convert))
            Future.successful(newUser)

          case None =>
            val newUser = User(convert, List(convert))
            users = users + ((user.providerId, user.userId) -> newUser)
            Future.successful(newUser)
        }

      case SaveMode.PasswordChange =>
        /* TODO:
        findProfile(user).map { entry => updateProfile(user, entry) }.getOrElse(
          // this should not happen as the profile will be there
          throw new Exception("missing profile)")
        )
        */
        val newUser = User(convert, List(convert))
        Future.successful(newUser)
    }
  }

  def link(current: User, to: BasicProfile): Future[User] = {
    if (current.identities.exists(i => i.providerId == to.providerId && i.userId == to.userId)) {
      Future.successful(current)
    } else {
      /* TODO: ..
      val added = to :: current.identities
      val updatedUser = current.copy(identities = added)
      users = users + ((current.main.providerId, current.main.userId) -> updatedUser)
      Future.successful(updatedUser)
      */
      Future.successful(current)
    }
  }

  def saveToken(token: MailToken): Future[MailToken] = {
    Future.successful {
      tokens += (token.uuid -> token)
      token
    }
  }

  def findToken(token: String): Future[Option[MailToken]] = {
    Future.successful { tokens.get(token) }
  }

  def deleteToken(uuid: String): Future[Option[MailToken]] = {
    Future.successful {
      tokens.get(uuid) match {
        case Some(token) =>
          tokens -= uuid
          Some(token)
        case None => None
      }
    }
  }

  //  def deleteTokens(): Future {
  //    tokens = Map()
  //  }

  def deleteExpiredTokens() {
    tokens = tokens.filter(!_._2.isExpired)
  }

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] = {
    import ProfileImplicits._
    Future.successful {
      for (
        found <- users.values.find(_ == user);
        identityWithPasswordInfo <- found.identities.find(_.providerId == UsernamePasswordProvider.UsernamePassword)
      ) yield {
        val idx = found.identities.indexOf(identityWithPasswordInfo)
        val updated = identityWithPasswordInfo.copy(passwordInfo = Some(info))
        val updatedIdentities = found.identities.patch(idx, Seq(updated), 1)
        val updatedEntry = found.copy(identities = updatedIdentities)
        users = users + ((updatedEntry.main.providerId, updatedEntry.main.userId) -> updatedEntry)
        updated

      }
    }
  }

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = {
    import ProfileImplicits._
    Future.successful {
      for (
        found <- users.values.find(u => u.main.providerId == user.main.providerId && u.main.userId == user.main.userId);
        identityWithPasswordInfo <- found.identities.find(_.providerId == UsernamePasswordProvider.UsernamePassword)
      ) yield {
        identityWithPasswordInfo.passwordInfo.get
      }
    }
  }
}
