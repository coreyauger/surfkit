import java.lang.reflect.Constructor

import scala.language.postfixOps

import controllers.CustomRoutesService
import java.lang.reflect.Constructor
import securesocial.core.RuntimeEnvironment

import service._
import io.surfkit.model.Auth.User


object Global extends play.api.GlobalSettings {

  object MyRuntimeEnvironment extends RuntimeEnvironment.Default[User] {
    override lazy val routes = new CustomRoutesService()
    override lazy val userService: SurfKitUserService = new SurfKitUserService()
    override lazy val eventListeners = List(new SecureSocialEventListener())
  }

  /**
   * An implementation that checks if the controller expects a RuntimeEnvironment and
   * passes the instance to it if required.
   *
   * This can be replaced by any DI framework to inject it differently.
   *
   * @param controllerClass
   * @tparam A
   * @return
   */
  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    val instance  = controllerClass.getConstructors.find { c =>
      val params = c.getParameterTypes
      params.length == 1 && params(0) == classOf[RuntimeEnvironment[User]]
    }.map {
      con =>
        con.asInstanceOf[Constructor[A]].newInstance(MyRuntimeEnvironment)
    }
    instance.getOrElse(super.getControllerInstance(controllerClass))
  }

  override def onStart(app: play.api.Application) {
    val startup =
      """
        |  _________              _____ ____  __.__  __     .__
        | /   _____/__ __________/ ____\    |/ _|__|/  |_   |__| ____
        | \_____  \|  |  \_  __ \   __\|      < |  \   __\  |  |/  _ \
        | /        \  |  /|  | \/|  |  |    |  \|  ||  |    |  (  <_> )
        |/_______  /____/ |__|   |__|  |____|__ \__||__| /\ |__|\____/
        |        \/                            \/        \/
      """.stripMargin
    print(startup)
    println("")

    // Put your Project Init here...

  }
}
