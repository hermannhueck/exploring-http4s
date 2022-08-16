import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

val helloWorldService = HttpRoutes.of[IO] { case GET -> Root / "hello" / name =>
  Ok(s"Hello, $name.")
}

case class Tweet(id: Int, message: String)

implicit def tweetEncoder: EntityEncoder[IO, Tweet]       = ???
implicit def tweetsEncoder: EntityEncoder[IO, Seq[Tweet]] = ???

def getTweet(tweetId: Int): IO[Tweet]  = ???
def getPopularTweets(): IO[Seq[Tweet]] = ???

val tweetService = HttpRoutes.of[IO] {
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

val services = tweetService <+> helloWorldService

val httpApp = Router("/" -> helloWorldService, "/api" -> services).orNotFound

val server = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8080")
  .withHttpApp(httpApp)
  .build

// val (server, shutdown) = server.allocated.unsafeRunSync()

// shutdown.unsafeRunSync()
