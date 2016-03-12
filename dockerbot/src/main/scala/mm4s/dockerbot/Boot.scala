package mm4s.dockerbot

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.rxthings.di._
import com.typesafe.scalalogging.LazyLogging
import mm4s.api.Streams._
import mm4s.api.UserModels.LoginByUsername
import mm4s.api.Users
import mm4s.bots.Mattermost
import mm4s.bots.api.{Bot, Connected}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Bootstrap a MM Bot
 */
object Boot extends App with LazyLogging {
  val config = Configuration.build()

  // todo;; should have configured system name
  val sysname = UUID.randomUUID.toString.take(7)
  implicit val system: ActorSystem = ActorSystem(s"dockerbot-$sysname", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // todo;; better pattern for required vars
  import ConfigKeys._
  for {
    host <- config.getAs[String](key.host);
    port <- config.getAs[Int](key.port);
    user <- config.getAs[String](key.user);
    pass <- config.getAs[String](key.pass);
    team <- config.getAs[String](key.team);
    channel <- config.getAs[String](key.channel)
  } yield (host, port, user, pass, team, channel) match {
    case (_, _, _, _, _, _) =>
      println(s"host:$host port:$port user:$user pass:$pass team:$team chan:$channel")

      val conn: Flow[HttpRequest, HttpResponse, _] = connection(host, port)

      val bot: ActorRef = injectActor[Bot]
      val done = Connected(bot, conn /* hack;; dont like conn here */)

      Users.login(LoginByUsername(user, pass, team))
      .via(conn)
      .via(Users.extractSession())
      .runWith(Sink.actorRef(Mattermost(channel), done))
    case _ =>
      system.terminate()
  }

  Await.ready(system.whenTerminated, Duration.Inf)
}
