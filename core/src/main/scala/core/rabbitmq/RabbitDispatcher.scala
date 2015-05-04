package core.rabbitmq

import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import akka.actor._
import core.rabbitmq.RabbitDispatcher._

import play.api.libs.json.JsValue

import com.rabbitmq.client.{Connection, ConnectionFactory, ShutdownListener, ShutdownSignalException}
import com.rabbitmq.client.{Address => RabbitAddress}

import scala.collection.immutable.Queue
import scala.annotation.tailrec

object RabbitConfig {
  
  val userExchange = "surfkit.users"
  
}

object RabbitDispatcher {

  case object Connect
  case object GetConnection
  case class Send(receiverUuid: String, provider: String, json: JsValue)
  
  sealed trait State
  case object Connected extends State
  case object Disconnected extends State
  
  sealed trait Data
  case object NoBrokerConnection extends Data
  case class BrokerConnection(underlying: com.rabbitmq.client.Connection, publisher: ActorRef) extends Data
  
  def props(address: RabbitAddress) = Props(new RabbitDispatcher(address))
}

class RabbitDispatcher(address: RabbitAddress) extends Actor with FSM[State, Data] with ActorLogging {
  
  val reconnectIn = 10 seconds
  
  val factory = {
    val cf = new ConnectionFactory()
    cf.setHost(address.getHost())
    cf.setPort(address.getPort())
    cf.setAutomaticRecoveryEnabled(true)
    cf
  }
  
  var msgBuffer: Queue[Send] = Queue.empty
  
  val shutdownListener = new ShutdownListener {
    override def shutdownCompleted(cause: ShutdownSignalException) {
      log.warning("received ShutdownSignalException from RabbitMQ")
      log.warning(s"reason: ${cause.getReason}")
    }
  }
  
  startWith(Disconnected, NoBrokerConnection)
  
  when(Disconnected) {
    case Event(RabbitDispatcher.Connect, _) =>
      log.info("Connecting to RabbitMQ...")
      Try { factory.newConnection() } match {
        case Success(conn) =>
          conn.addShutdownListener(shutdownListener)
          val publisher = context.actorOf(RabbitPublisher.props(conn.createChannel()))
          goto(Connected) using BrokerConnection(conn, publisher)
        case Failure(f) =>
          log.error(f, s"Couldn't connect to RabbitMQ server at $address")
          log.info(s"Reconnecting in $reconnectIn")
          setTimer("RabbitMQ reconnection", RabbitDispatcher.Connect, reconnectIn, false)
          stay
      }
    case Event(msg: Send, _) =>
      msgBuffer = msgBuffer.enqueue(msg)
      stay
  }
  
  when(Connected) {
    case Event(Send(uuid, provider, msg), BrokerConnection(_, publisher)) =>
      publisher ! RabbitPublisher.RabbitMessage(uuid, provider, msg)
      stay
    case Event(GetConnection, BrokerConnection(conn, _)) =>
      sender() ! conn
      stay
      
  }
  
  onTransition {
    //this should happen only once, as the RabbitMQ client lib is handling connection autorecovery later on
    case Disconnected -> Connected => 
      log.info(s"Connected to RabbitMQ broker at $address")
      nextStateData match {
        case BrokerConnection(conn, publisher) =>
          //send all messages
          msgBuffer = send(msgBuffer, publisher)
        case _ => log.error("RabbitConnectionActor in state Connected but without a connection!!!")
      }
  }
  
  @tailrec
  private[this] def send(buffer: Queue[Send], publisher: ActorRef): Queue[Send] =
    buffer.dequeueOption match {
      case Some((Send(uuid, provider, msg), buff)) =>
        publisher ! RabbitPublisher.RabbitMessage(uuid, provider, msg)
        send(buff, publisher)
      case None => buffer
    }
  
  override def preStart() = self ! RabbitDispatcher.Connect
  
  override def postStop() =
    stateData match {
      case BrokerConnection(conn, _) =>
        log.info(s"Closing connection to RabbitMQ on $address")
        conn.close()
      case _ => //there's no connection to close
    }
}