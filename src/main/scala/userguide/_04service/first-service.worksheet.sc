import org.http4s.server.Server
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

val helloWorldService: HttpRoutes[IO] =
  HttpRoutes.of[IO] { case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
  }

import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._

case class Tweet(id: Int, message: String)

implicit val tweetEncoder: Encoder[Tweet]                 =
  deriveEncoder[Tweet]
implicit def tweetEntityEncoder: EntityEncoder[IO, Tweet] =
  jsonEncoderOf

implicit def tweetsEntityEncoder: EntityEncoder[IO, Seq[Tweet]] =
  jsonEncoderOf

def getTweet(tweetId: Int): IO[Tweet]  = ???
def getPopularTweets(): IO[Seq[Tweet]] = ???

val tweetService: HttpRoutes[IO] =
  HttpRoutes.of[IO] {
    case GET -> Root / "tweets" / "popular"       =>
      getPopularTweets().flatMap(Ok(_))
    case GET -> Root / "tweets" / IntVar(tweetId) =>
      getTweet(tweetId).flatMap(Ok(_))
  }

import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router
import scala.concurrent.duration._

val services: HttpRoutes[IO] =
  tweetService <+> helloWorldService

val httpApp: HttpApp[IO] =
  Router("/" -> helloWorldService, "/api" -> services).orNotFound

val server: Resource[IO, Server] =
  EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(httpApp)
    .build

// val (server, shutdown) = server.allocated.unsafeRunSync()

// shutdown.unsafeRunSync()
