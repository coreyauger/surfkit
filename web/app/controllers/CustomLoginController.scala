package controllers

import securesocial.controllers.{ProviderControllerHelper, BaseLoginPage}
import play.api.mvc.{RequestHeader, AnyContent, Action}
import play.api.Logger
import securesocial.core.{SecureSocial, RuntimeEnvironment, IdentityProvider}
import io.surfkit.model.Auth.User
import securesocial.core.services.RoutesService
import securesocial.core.providers.UsernamePasswordProvider

class CustomLoginController(implicit override val env: RuntimeEnvironment[User]) extends BaseLoginPage[User] {
  override def login: Action[AnyContent] = {
    Logger.debug("using CustomLoginController")
    //super.login
    login2
  }


  def login2 = UserAwareAction { implicit request =>
    val to = ProviderControllerHelper.landingUrl
    if ( request.user.isDefined && request.user.get.main.email != "") {
      // if the user is already logged in just redirect to the app
      //logger.debug("User already logged in, skipping login page. Redirecting to %s".format(to))
      Redirect( to )
    } else {
      if ( SecureSocial.enableRefererAsOriginalUrl ) {
        SecureSocial.withRefererAsOriginalUrl(Ok(env.viewTemplates.getLoginPage(UsernamePasswordProvider.loginForm)))
      } else {
        //Ok(env.viewTemplates.getLoginPage(UsernamePasswordProvider.loginForm))
        Ok(views.html.site.login(UsernamePasswordProvider.loginForm))
      }
    }
  }
}


class CustomRoutesService extends RoutesService.Default {
  override def loginPageUrl(implicit req: RequestHeader): String = controllers.routes.CustomLoginController.login().absoluteURL(IdentityProvider.sslEnabled)
}
