package controllers

import io.surfkit.model.Auth.{User, SurfKitUser}
import play.api.mvc._
import scala.concurrent.Future
import securesocial.core._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws._
import play.api.Play.current

class Application(override implicit val env: RuntimeEnvironment[User]) extends SecureSocial[User]{

  def index = SecuredAction {
    implicit request =>
    Ok(views.html.main(request.user.main.id))
  }

  def lookup(ip:String) = Action.async{
    WS.url(s"http://localhost:9999/ip/${ip}").get.map{
      response =>
        Ok(response.json)
    }
  }

}
