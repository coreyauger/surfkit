package service

/**
 * Created by suroot on 04/05/15.
 */

import io.surfkit.model.{Api, Auth}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import play.api.{Play, Logger}
import play.api.libs.ws.WS
import securesocial.core._
import securesocial.core.providers.{ UsernamePasswordProvider, MailToken }
import scala.concurrent.Future
import securesocial.core.services.{ UserService, SaveMode }

import io.surfkit.model.Auth.{SurfKitUser, User, ProviderProfile}

import scala.util.Try

object ProfileImplicits {
  implicit def BasicProfile2ProviderProfile(u : BasicProfile):ProviderProfile =
    ProviderProfile(
      0,0,"APPID",
      u.providerId,
      u.userId,
      u.firstName,
      u.lastName,
      u.fullName,
      u.email,
      u.avatarUrl,
      io.surfkit.model.Auth.AuthenticationMethod(u.authMethod.method),
      u.oAuth1Info.map( o =>io.surfkit.model.Auth.OAuth1Info(o.token, o.secret)),
      u.oAuth2Info.map( o =>io.surfkit.model.Auth.OAuth2Info(o.accessToken, o.tokenType, o.expiresIn, o.refreshToken) ),
      u.passwordInfo.map( p =>io.surfkit.model.Auth.PasswordInfo(p.hasher, p.password, p.salt) )
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

  implicit def PasswordInfo2SkPassworInfo(p: PasswordInfo):io.surfkit.model.Auth.PasswordInfo =
   io.surfkit.model.Auth.PasswordInfo(p.hasher, p.password, p.salt)

  implicit def SkPassworInfo2PasswordInfo(p:io.surfkit.model.Auth.PasswordInfo):PasswordInfo  =
    PasswordInfo(p.hasher, p.password, p.salt)
/*
  // CA - this is required....
  import io.surfkit.model.Auth._


  implicit val pir    = Json.reads[io.surfkit.model.Auth.PasswordInfo]
  implicit val piw    = Json.writes[io.surfkit.model.Auth.PasswordInfo]

  implicit val oa1r    = Json.reads[io.surfkit.model.Auth.OAuth1Info]
  implicit val oa1w    = Json.writes[io.surfkit.model.Auth.OAuth1Info]

  implicit val oa2r    = Json.reads[io.surfkit.model.Auth.OAuth2Info]
  implicit val oa2w    = Json.writes[io.surfkit.model.Auth.OAuth2Info]

  implicit val amr    = Json.reads[io.surfkit.model.Auth.AuthenticationMethod]
  implicit val amw    = Json.writes[io.surfkit.model.Auth.AuthenticationMethod]

  implicit val pr     = Json.reads[io.surfkit.model.Auth.ProviderProfile]
  implicit val pw     = Json.writes[io.surfkit.model.Auth.ProviderProfile]
  //implicit val ur     = Json.reads[io.surfkit.model.Auth.User]

  implicit val rw     = Json.writes[io.surfkit.model.Auth.FindUser]

  implicit val rsr    = Json.reads[io.surfkit.model.Auth.SaveResponse]
  */
}


class SurfKitUserService extends UserService[User] {
  val logger = Logger("application.controllers.SurfKitUserService")

  lazy val surfkitEndpoint = s"http://${Play.current.configuration.getString("surfkit.core.host").getOrElse("localhost")}:${Play.current.configuration.getString("surfkit.core.port").getOrElse(8080)}/v1/api"

  var users = Map[(String, String), User]()
  //private var identities = Map[String, BasicProfile]()
  private var tokens = Map[String, MailToken]()

  def find(providerId: String, userId: String): Future[Option[BasicProfile]] = {
    logger.info(s"SurfKitUserService.find($providerId, $userId)")
    if (logger.isDebugEnabled) {
      logger.debug("users = %s".format(users))
    }
    WS.url(s"$surfkitEndpoint/auth/find").post( upickle.write(io.surfkit.model.Auth.FindUser("APPID",providerId,userId)) ).map{
      res =>
        println(s"ret json: ${res.json}")
        val apiRes = upickle.read[Api.Result](res.json.toString)
        ProfileImplicits.ProviderProfile2BasicProfile(Try(upickle.read[ProviderProfile](apiRes.data)).toOption)
    }
  }


  def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] = {
    import ProfileImplicits._
    /*
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
    */
    Future.successful(None)
  }

  private def saveProfile(user: BasicProfile):Future[User] = {
    import ProfileImplicits._
    WS.url(s"$surfkitEndpoint/auth/save").post(upickle.write( ProfileImplicits.BasicProfile2ProviderProfile(user))).map {
      res =>
        println(s"ret json: ${res.json}")
        // TODO: if userId return is zero then we failed to save ...
        val apiRes = upickle.read[Api.Result](res.json.toString)
        val sr = upickle.read[io.surfkit.model.Auth.SaveResponse](apiRes.data)
        User(SurfKitUser(sr.userId, "token", user.fullName, user.avatarUrl, user.email), List[ProviderProfile]())
    }
  }

  def save(user: BasicProfile, mode: SaveMode): Future[User] = {
    println("SurfKitUserService.save")
    val convert = ProfileImplicits.BasicProfile2ProviderProfile(user)
    mode match {
      case SaveMode.SignUp =>
        println("... calling save.")
        saveProfile(user)

      case SaveMode.LoggedIn =>
        // first see if there is a user with this BasicProfile already.
        find(user.providerId, user.userId).flatMap{
          case Some(existingUser) =>
            println("existingUser ... do update")
            saveProfile(user)

          case None =>
            println("None ... calling save.")
            saveProfile(user)
        }

      case SaveMode.PasswordChange =>
        /* TODO:
        findProfile(user).map { entry => updateProfile(user, entry) }.getOrElse(
          // this should not happen as the profile will be there
          throw new Exception("missing profile)")
        )
        */
        val newUser = User(SurfKitUser(0,"token",Some("full Name"),Some("avatar"),Some("email")),List(convert))
        Future.successful(newUser)
    }
  }

  def link(current: User, to: BasicProfile): Future[User] = {
    if(true){
    //if (current.identities.exists(i => i.providerId == to.providerId && i.userId == to.userId)) {
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
      /*
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
      */
      None
    }
  }

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = {
    import ProfileImplicits._
    Future.successful {
      /*
      for (
        found <- users.values.find(u => u.main.providerId == user.main.providerId && u.main.userId == user.main.userId);
        identityWithPasswordInfo <- found.identities.find(_.providerId == UsernamePasswordProvider.UsernamePassword)
      ) yield {
        identityWithPasswordInfo.passwordInfo.get
      }
      */

      None
    }
  }
}
