package core.api.modules


import akka.actor.ActorRef
import com.ning.http.client.AsyncHttpClientConfig
import io.surfkit.core.api.AbstractSystem
import io.surfkit.core.rabbitmq.RabbitDispatcher
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import play.api.libs.json._
import io.surfkit.model.Api._
import io.surfkit.model.{Api, Model}
import play.api.libs.ws.DefaultWSClientConfig
import play.api.libs.ws.ning.{NingWSClient, NingAsyncHttpClientConfigBuilder}

import scala.concurrent.Future

/**
 * Created by suroot on 08/05/15.
 */
trait SurfKitModule extends AbstractSystem {
  this : AbstractSystem =>

  val wsConfig = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
  val wsBuilder = new AsyncHttpClientConfig.Builder(wsConfig)


  // CA - must override these 2 at minimum to have a functioning module
  def module():String
  def mapper(r:Api.Request):Future[Api.Result]

  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)


  // Let's Wax !
  val sysDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(config.getString("rabbitmq.host"), config.getInt("rabbitmq.port"))))
  sysDispatcher ! RabbitDispatcher.ConnectModule(module, mapper)  // connect to the MQ
  // TODO: don't like the multiple dispatcher bit :(
  val userDispatcher = system.actorOf(RabbitDispatcher.props(RabbitMqAddress(config.getString("rabbitmq.host"), config.getInt("rabbitmq.port"))))
  userDispatcher ! RabbitDispatcher.Connect  // connect to the MQ
}

