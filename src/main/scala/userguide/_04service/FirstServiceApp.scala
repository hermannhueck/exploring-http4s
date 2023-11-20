package userguide._04service

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router

object FirstServiceApp extends IOApp {

  val helloWorldService =
    HttpRoutes.of[IO] { case GET -> Root / "hello" / name =>
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

  val services = tweetService <+> helloWorldService

  val httpApp = Router("/" -> helloWorldService, "/api" -> services).orNotFound

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val server = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(httpApp)
    .build

  override def run(args: List[String]): IO[ExitCode] =
    server
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
