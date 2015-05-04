package service

/**
 * Created by suroot on 04/05/15.
 */


import securesocial.core._
import play.api.mvc.{Session, RequestHeader}
import play.api.Logger
import io.surfkit.model.User

/**
 * A sample event listener
 */
class SecureSocialEventListener extends EventListener[User] {
  def onEvent(event: Event[User], request: RequestHeader, session: Session): Option[Session] = {
    val eventName = event match {
      case LoginEvent(u) => "login"
      case LogoutEvent(u) => "logout"
      case SignUpEvent(u) => "signup"
      case PasswordResetEvent(u) => "password reset"
      case PasswordChangeEvent(u) => "password change"
    }

    Logger.info("traced %s event for user %s".format(eventName, event.user.main.userId))

    // retrieving the current language
    Logger.info("current language is %s".format(request2lang(request)))

    // Not changing the session so just return None
    // if you wanted to change the session then you'd do something like
    // Some(session + ("your_key" -> "your_value"))
    None
  }
}

