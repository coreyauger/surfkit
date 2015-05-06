package controllers

import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws._
import play.api.Play.current

object Application extends Controller {



  def index = Action {
    Ok(views.html.main())
  }

  def lookup(ip:String) = Action.async{
    WS.url(s"http://localhost:9999/ip/${ip}").get.map{
      response =>
        Ok(response.json)
    }
  }

}
