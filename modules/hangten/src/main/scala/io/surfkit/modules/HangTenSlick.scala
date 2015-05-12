package io.surfkit.modules

import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import io.surfkit.model._
import io.surfkit.model.Auth._

/**
 * Created by suroot on 08/05/15.
 */

object HangTenSlick{

  val db = Database.forURL("jdbc:postgresql://localhost:5432/sktest", user = "postgres", password = "Neverdull42",
    driver = "org.postgresql.Driver",  executor = AsyncExecutor("executor1", numThreads=10, queueSize=1000))
  // Definition of the Provider table
  class SurfKitUser(tag: Tag) extends Table[Auth.SurfKitUser](tag, "Users") {
    def id = column[Long]("user_id", O.PrimaryKey, O.AutoInc)
    def token = column[String]("user_token")
    def fullName = column[Option[String]]("user_fullName")
    def avatarUrl = column[Option[String]]("user_avatarUrl")
    def email = column[Option[String]]("user_email")

    def * = (id, token, fullName, avatarUrl, email) <> (Auth.SurfKitUser.tupled, Auth.SurfKitUser.unapply)

  }
  val users = TableQuery[SurfKitUser]

  def getUser(id: Long) = users.filter { _.id === id }

  /*
  class Coffees(tag: Tag) extends Table[(String, Double)](tag, "COFFEES") {
    def name = column[String]("COF_NAME")
    def price = column[Double]("PRICE")
    def * = (name, price)
  }
  val coffees = TableQuery[Coffees]

  val q = coffees.filter(_.price > 8.0).map(_.name)
*/

  case class FlatProviderProfile(
      id:Long,
      UserKey:Long,
      appId:String,
      providerId:String,
      userId:String,
      firstName:Option[String],
      lastName:Option[String],
      fullName:Option[String],
      avatarUrl:Option[String],
      email:Option[String],
      authMethod:String,
      OAuth1InfoToken:Option[String],
      OAuth1InfoSecret:Option[String],
      OAuth2InfoAccessToken:Option[String],
      OAuth2InfoTokenType:Option[String],
      OAuth2InfoExpiresIn:Option[Int],
      OAuth2InfoRefreshToken:Option[String],
      PasswordInfoHasher:Option[String],
      PasswordInfoPassword:Option[String],
      PasswordSalt:Option[String])
  object Implicits {

    implicit def ProviderToFlatProvider(p: Auth.ProviderProfile, uid:Long = 0): FlatProviderProfile = {
      FlatProviderProfile(
        p.id,
        p.userKey match{
          case 0 => uid
          case _ => p.userKey
        },
        p.appId,
        p.providerId,
        p.userId,
        p.firstName,
        p.lastName,
        p.fullName,
        p.email,
        p.avatarUrl,
        p.authMethod match{
          case Auth.AuthenticationMethod.OAuth1 => "oauth1"
          case Auth.AuthenticationMethod.OAuth2 => "oauth2"
          case Auth.AuthenticationMethod.OpenId => "openId"
          case _ => "userPassword"
        },
        p.oAuth1Info.map(_.token),
        p.oAuth1Info.map(_.secret),
        p.oAuth2Info.map(_.accessToken),
        p.oAuth2Info.map(_.tokenType.getOrElse("")),
        p.oAuth2Info.map(_.expiresIn.getOrElse(0)),
        p.oAuth2Info.map(_.refreshToken.getOrElse("")),
        p.passwordInfo.map(_.hasher),
        p.passwordInfo.map(_.password),
        p.passwordInfo.map(_.salt.getOrElse(""))

      )

    }

    implicit def FlatProviderToProvider(p: FlatProviderProfile): Auth.ProviderProfile =
      Auth.ProviderProfile(
        p.id,
        p.UserKey,
        p.appId,
        p.providerId,
        p.userId,
        p.firstName,
        p.lastName,
        p.fullName,
        p.email,
        p.avatarUrl,
        Auth.AuthenticationMethod(p.authMethod),
        p.OAuth1InfoToken.map(_ => Auth.OAuth1Info(p.OAuth1InfoToken.getOrElse(""), p.OAuth1InfoSecret.getOrElse(""))),
        p.OAuth2InfoAccessToken.map(_ => Auth.OAuth2Info(p.OAuth2InfoAccessToken.getOrElse(""), p.OAuth2InfoTokenType, p.OAuth2InfoExpiresIn, p.OAuth2InfoRefreshToken)),
        p.PasswordInfoPassword.map(_ => Auth.PasswordInfo(p.PasswordInfoHasher.getOrElse(""), p.PasswordInfoPassword.getOrElse(""), p.PasswordSalt))
      )
  }
  // Definition of the Provider table
  class Provider(tag: Tag) extends Table[FlatProviderProfile](tag, "Providers") {
    def id = column[Long]("provider_id", O.PrimaryKey, O.AutoInc)
    def userKey = column[Long]("provider_user_key")
    def appId = column[String]("provider_appId")
    def providerId = column[String]("provider_providerId")
    def userId = column[String]("provider_userId")
    def firstName = column[Option[String]]("provider_firstName")
    def lastName = column[Option[String]]("provider_lastName")
    def fullName = column[Option[String]]("provider_fullName")
    def email = column[Option[String]]("provider_email")
    def avatarUrl = column[Option[String]]("provider_avatarUrl")
    def authMethod = column[String]("provider_authMethod")
    def OAuth1InfoToken = column[Option[String]]("provider_OAuth1InfoToken")
    def OAuth1InfoSecret = column[Option[String]]("provider_OAuth1InfoSecret")
    def OAuth2InfoAccessToken = column[Option[String]]("provider_OAuth2InfoAccessToken")
    def OAuth2InfoTokenType = column[Option[String]]("provider_OAuth2InfoTokenType")
    def OAuth2InfoExpiresIn = column[Option[Int]]("provider_OAuth2InfoExpiresIn")
    def OAuth2InfoRefreshToken = column[Option[String]]("provider_OAuth2InfoRefreshToken")
    def PasswordInfoHasher = column[Option[String]]("provider_PasswordInfoHasher")
    def PasswordInfoPassword = column[Option[String]]("provider_PasswordInfoPassword")
    def PasswordSalt = column[Option[String]]("provider_PasswordSalt")

    def * = (
      id,
      userKey,
      appId,
      providerId,
      userId,
      firstName,
      lastName,
      fullName,
      email,
      avatarUrl,
      authMethod,
      OAuth1InfoToken,
      OAuth1InfoSecret,
      OAuth2InfoAccessToken,
      OAuth2InfoTokenType,
      OAuth2InfoExpiresIn,
      OAuth2InfoRefreshToken,
      PasswordInfoHasher,
      PasswordInfoPassword,
      PasswordSalt)  <> (FlatProviderProfile.tupled, FlatProviderProfile.unapply)
    // A reified foreign key relation that can be navigated to create a join
    def User = foreignKey("provider_User_FK", userKey, users)(_.id)

    //def idx = index("idx_a", (k1, k2), unique = true)
  }
  val providers = TableQuery[Provider]




  def getProvider(id: Long) = providers.filter { _.id === id }
  def getProvider(app:String, providerId: String, userId: String) = {
    db.run( providers.filter { p => (p.appId === app && p.providerId === providerId && p.userId === userId)}.result )
  }
  def saveProvider(p:Auth.ProviderProfile):Future[Long] = {
    println("RUNNING SAVE")
    val userId = (users returning users.map(_.id)) += Auth.SurfKitUser(0,"token", p.fullName, p.avatarUrl,p.email)
    db.run( userId ).flatMap{
      case uid =>
        val aa = Implicits.ProviderToFlatProvider(p, uid)
        db.run( DBIO.seq(providers += aa )  ).map(_ => uid).recover{ case _ => 0L }
    }
  }




  val setup = DBIO.seq(
    // Create the tables, including primary and foreign keys
    (users.schema ++ providers.schema).create
    // Insert some suppliers
    //users += Auth.SurfKitUser(0,"token", Some("corey auger"), Some("avatar"), Some("email"))
    // Equivalent SQL code:
    // insert into COFFEES(COF_NAME, SUP_ID, PRICE, SALES, TOTAL) values (?,?,?,?,?)
  )

  val setupFuture = db.run(setup)
  println("RUNNING DB SETUP........................................")
  setupFuture.onComplete{
    case Success(s) =>
      println(s"Success($s)")
    case Failure(t) =>
      println(s"Failure($t)")

  }

}


class HangTenSlick {

}
