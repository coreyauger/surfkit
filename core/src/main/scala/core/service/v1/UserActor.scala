package io.surfkit.core.service.v1

import java.text.SimpleDateFormat
import java.util.TimeZone

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.surfkit.model.{Api, Auth}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.rabbitmq.client.Connection
import io.surfkit.core.Configuration
import io.surfkit.core.rabbitmq.RabbitDispatcher.RabbitMqAddress
import io.surfkit.core.rabbitmq.{RabbitDispatcher, RabbitUserConsumer}

import scala.util.{Failure, Success}


class UserActor(uid: Long, channelId:Api.Route, rabbitDispatcher: ActorRef) extends Actor with ActorLogging{

  log.info(s"UserActor create: $uid")

  var channels = channelId :: Nil
  var socketChannel:ActorRef = null

  implicit val timeout = new Timeout(3 seconds)
  // NOTE: That this strategy has been adopted to deal with Provider failures below.
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 2 minute) {
      //case _: XMPPException     =>
      //  log.warning("XMPPException doing a restart............................................................")
      //  Restart
      case e: Exception         =>
        log.error(e.toString)
        Restart
      //case _: Exception         => Escalate
    }

  //register with RabbitMQ
  def startConsumer(): Unit = (rabbitDispatcher ? RabbitDispatcher.GetConnection).mapTo[Connection].onComplete {
    case Success(conn:com.rabbitmq.client.Connection) =>
      log.info("Got connection from RabbitDispatcher.")
      // Store ref to actor so we can push data onto rabbit that the socket will get.
      socketChannel = context.actorOf(RabbitUserConsumer.props(uid, self)(conn))
      // CA - we now "absorb" any other user actors of the same id taking over their channels..
      // TODO: app id ??
      val a = Auth.AbsorbActorReq(channelId)
      val req = Api.Request("auth","register", upickle.write(a), Api.Route("","",0L) )
      println("Sending Absorb to RabbitDispatcher...")
      rabbitDispatcher ! RabbitDispatcher.SendUser(uid,"appId",req)
    case Failure(_) =>
      log.info("Failed to get a connection from RabbitDispatcher. Will try again in 5 seconds.")
      context.system.scheduler.scheduleOnce(5.second) {
        startConsumer()
      }
  }
  startConsumer()

  // This pushes data back into rabbit that will go down the web socket connections to the user.
  def pushToConnectedChannels(res:Api.Result) = channels.foreach(
    channel =>
      // No CorrId .. just a reply key (which is the id of the socket)
      socketChannel ! Api.Result(res.status, res.module, res.op, res.data, channel )
    )

  /*
  val defaultProviders = List[(String, () => ActorRef)](
    ("walkabout", () => context.actorOf(Props(new WalkaboutChat(self, uuid.toLong)),s"walkabout_$uuid") ),
    ("email", () => context.actorOf(Props(new EmailChat(self, uuid)),s"email_$uuid") ),
    ("phone", () => context.actorOf(Props(new PhoneChat(self, uuid)),s"phone_$uuid") )
  )
  val thirdPartyProviders =  List[(String, (String, JID) => ActorRef)](
    ("linkedin", (token, notThisJid) => context.actorOf(Props(new LinkedInChat(self, jid, uuid)),s"linkedin_$uuid") ),
    ("twitter", (token, notThisJid) => context.actorOf(Props(new TwitterChat(self, jid, uuid)),s"twitter_$uuid") ),
    ("google", (token, jid) => context.actorOf(Props(new ChatSupervisor("google", uuid, self, jid, "123456", token, Some("talkx.l.google.com"))),s"google_$uuid") )
  )

  def refreshChatProviders = {
    log.debug("refreshChatProviders")
    defaultProviders.foreach{
      case (p,createActor) =>
        if( chatProviders.get(p) == None ){
          log.debug(s"Create $p for $jid")
          chatProviders += (p -> createActor())
        }
    }
    thirdPartyProviders.foreach {
      case (p, createActor) =>
        if( chatProviders.get(p) == None ) {
          // NOTE: that find returns a future... and is therefor NOT safe to edit the Mutable state VAR chatProviders
          Neo4JUserService.findWithOAuth(uuid, p).map {
            case Some(user: BasicProfile) =>
              val jid = JID(Neo4JUserService.userProviderToJid(user.providerId, user))
              val oauth2: OAuth2Info = user.oAuth2Info.getOrElse(new OAuth2Info(""))
              val oauth1: OAuth1Info = user.oAuth1Info.getOrElse(new OAuth1Info("", ""))
              log.info(s"Creating provider ${user.providerId}")
              if (oauth2.accessToken != "") {
                context.self ! AddChatProvider(user.providerId, createActor(oauth2.accessToken, jid))
              } else if (oauth1.token != "") {
                context.self ! AddChatProvider(user.providerId, createActor(oauth1.token, jid))
              } else {
                log.info(s"No OAuth found for ${user.providerId}.  Skipping ChatSupervisor ")
              }
            case None => log.info(s"No $p account for user $jid") // do nothing.. ie don't create
          }
        }
    }
  }
  refreshChatProviders


  def chatSend(chatid:Long, msg:String, app:JsValue, oerrideProvider: Option[String] = None ) ={
    val m = new Message(JID(jid), "walkabout", msg, chatid, userInfo.fullName.getOrElse(""), userInfo.avatarUrl.getOrElse(""), None, 0L, new java.util.Date(), app)
    println(s"about to call router on chat $chatid")
    UserActor.routeConfigForChat(chatid) match {
      case Some(route) =>
        //route ! WalkaboutRouteMessage(m)
        route ! RouteMessage(uuid.toLong, m, oerrideProvider)
      case _ => throw new Exception(s"No Chat router for chat $chatid")
    }
  }
  */

  val dateFormatUtc: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  dateFormatUtc.setTimeZone(TimeZone.getTimeZone("UTC"))


  override def receive = {
    // CA - Safe way to "Lock" chatProviders map and make sure all get added correctly (1 at a time)
    /*
    case a:AddChatProvider =>
      chatProviders += (a.provider -> a.actor)
      println(s"${a.provider} !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
      println(chatProviders.values.toString)
    */

    /*
    case r: Refresh =>
      log.debug("UserActor::Refresh")
      if( r.recreateChatProviders ){
        // TODO: right now i force the reload of all of the providers.... this is an expensive operation
        // TODO: we should figure out only which ones will need it...
        //chatProviders.values.foreach( _ ! Kill )
        //chatProviders = Map[String, ActorRef]()  // <- this will force the creation of all possible below...
      }
      //  (CA) - just refresh all of these for now...
      // TODO: make it only refresh the needed service.
      //log.info("Refreshing service")
      //if( r.service == "notifiers" ){
      //}else if( r.service == "chatproviders"){
      refreshChatProviders
    //}
    */

    /*
    case c: UserSocketConnect =>
      log.info(s"UserActor::UserSocketConnect")
      socketmap += context.sender.path.name -> (c.socket, c.isComet)
      //userActor2 ! NewConnection(c.socket)
      sender ! c

    case d: UserSocketDisconnect =>
      log.info(s"UserActor::UserSocketDisconnect")
      socketmap -= context.sender.path.name
      sender ! d
      val numsockets = socketmap.size
      log.info(s"Num sockets left $numsockets for $uuid")
      // We terminate pseudo users only
      if( socketmap.isEmpty && jid.indexOf("@walkabout.im") == -1 ){
        // no more web socket connection. And this is a non walkabout user.. so terminate
        chatProviders.values.foreach( _ ! Kill )
        // cancellable.cancel()
        // TODO: this is a hack way to get psudeo actors unique to a chat
        val actorJid = if( !jid.endsWith("walkabout.im") && chatid != "0") "%s/%s".format(jid,chatid) else jid
        UserActor.usermap -= actorJid
        log.info(s"No more sockets USER SHUTDOWN.. good bye !")
        context.stop(self)
      }

    case om: OutboundMessage =>
      // we forward to the right ChatProvider to handle the message
      // provider -> "facebook", "google" ...
      chatProviders.get(om.provider) match{
        case Some(chatProvider) =>
          log.debug(s"Outbound message message routing to provider user(${om.uId}}): ${om.provider}")
          chatProvider ! om
        case None =>
          log.error(s"Got a message for ${om.provider} a provider that we do not have.  This should not happen ?????")
          log.error("Map of providers is: ")
          log.error(chatProviders.values.toString)
      }
      */
    case r:Auth.Echo =>
      pushToConnectedChannels(Api.Result(0, "User","echo",upickle.write(r),channelId))


    case r:Auth.AbsorbActorReq =>
      if( r.channelId != channelId ){
        // we need to be absorbed ...
        val a = Auth.AbsorbActorRes(channels)
        val req = Api.Request("auth","channels", upickle.write(a), Api.Route("","",0L) )
        println("Sending AbsorbActorRes RabbitDispatcher...")
        rabbitDispatcher ! RabbitDispatcher.SendUser(uid,"appId",req)
        // now we can shut down.. since there should only be one user actor.
        context.stop(self)
      }else {
        // Note that if the channel id was the same.. we do nothing..
        println("AbsorbActorReq from self...")
      }

    case r:Auth.AbsorbActorRes =>
      channels = channels ::: r.channels  // append the return channels..
      println("AbsorbActorRes")
      println(s"channels: ${channels}")

    case RabbitUserConsumer.RabbitMessage(deliveryTag, headers, body) =>
      val uid = headers("uid").toLong
      log.debug(s"Recieved RabbitMQ message for user $uid")
      log.debug(s"RabbitMessage($deliveryTag, $headers, ${body.utf8String}")
      val apiReq = upickle.read[Api.Request](body.utf8String)
      println(s"API REQUEST: ${apiReq}")
      apiReq.op match{
        case "register" => self ! upickle.read[Auth.AbsorbActorReq](apiReq.data)
        case "channels" => self ! upickle.read[Auth.AbsorbActorRes](apiReq.data)
        case "echo" => self ! upickle.read[Auth.Echo](apiReq.data)
      }
      /*
      chatProviders.get(provider) match {
        case Some(chatProvider) =>
          log.debug(s"Outbound message message routing to provider user($uid): $provider")
          chatProvider ! OutboundMessage(uid, provider, Message.fromJson(Json.parse(body.utf8String)))
        case None =>
          log.error(s"Got a message for $provider a provider that we do not have.  This should not happen ?????")
          log.error("Map of providers is: ")
          log.error(chatProviders.values.toString)
      }
      */
  }
}



object UserActor{

  def props(uuid: Long, channelId:Api.Route, rabbitDispatcher: ActorRef) =
    Props(new UserActor(uuid,channelId, rabbitDispatcher))

  /*
  def findUserActor(jid: String, chatid: Option[Long]):Option[ActorRef] ={
    // if we own this route.. then route it!
    // HACK: this is very hacky to find out if this is real user and not pseudo

    val actorLookup =
      if (jid.endsWith("@walkabout.im") || chatid == None)
        jid
      else
        "%s/%s".format(jid, chatid.get)
    println(s"Actor $actorLookup")
    println( usermap.keys )
    usermap.get(actorLookup)
  }
  */


  // TODO: for now this is a hack memory storage...
  // TODO: take me out of here... make me nicer :)
  /*
  private var chatRouter = Map[Long,ActorRef]()
  def routeConfigForChat( chatid: Long ): Option[ActorRef] = {
    if( !chatRouter.contains(chatid) ){
      println("create a new router...")
      val newrouter = Akka.system.actorOf(Props(new ChatRouter(chatid)))
      chatRouter += (chatid -> newrouter)
    }
    chatRouter.get(chatid)
  }
  def deleteChatRouter(chatid: Long) ={
    if( chatRouter.contains(chatid) ){
      chatRouter.get(chatid).get ! Kill
      chatRouter -= chatid
    }
  }

  def clearChatRouter() ={
    chatRouter.values.foreach(_ ! Kill)
    chatRouter = Map[Long,ActorRef]()
  }


  def route(jid:String, useractor:ActorRef, jr:JsonRequest) ={
    // TODO: think of a nicer way to broadcast to actors..
    val actors = if( jr.subjectOp == "api-api")(jr.json \ "data" \ "data" \ "actors").asOpt[JsArray]
    else (jr.json \ "data" \ "actors").asOpt[JsArray]  // if this is present then we want to send a message to another users actor..
    println( s"JsonRequest ${jid}: " + jr.json )
    println( "subjectOp: " + jr.subjectOp )
    if( actors != None ){
      // TODO: lookup actor on distributed system...
      // TODO: make sure user is allowed to send to actor
      val actorList = actors.get.as[List[String]]
      println("CALLING ACTORS *****************************")
      println(s"WebSocketActor::actors $actorList")
      actorList.foreach( actorJid => {
        val chatid = (jr.json \ "data" \ "chatid").asOpt[Long]
        println(chatid)

        val message = Json.obj(
          "from" -> jid,
          "slot" -> (jr.json \ "slot").as[String],
          "op" -> (jr.json \ "op").as[String],
          "data" -> (jr.json \ "data")
        )

        //rabbitDispatcher ! RabbitPublisher.RabbitMessage(actorJid, message)

        findUserActor(actorJid, chatid) match {
          case Some(actor) =>
            println("found actor... ")
            actor ! JsonRequest(jr.subjectOp, message)
          case None => println(s"Actor($actorJid) NOT found..doing nothing!"); usermap.foreach(println)// think about what we might want to do in this case.. (send email to user since they are not online)
        }

      })
    }else{
      useractor ! jr
    }
  }
  */
}

