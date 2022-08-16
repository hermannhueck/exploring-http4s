package userguide._04service

import cats.effect._
import org.http4s._
import cats._
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router

object FirstServiceApp2 extends IOApp {

  def helloWorldService[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = new org.http4s.dsl.Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
    }
  }

  case class Tweet(id: Int, message: String)

  implicit def tweetEncoder[F[_]]: EntityEncoder[F, Tweet]       = ???
  implicit def tweetsEncoder[F[_]]: EntityEncoder[F, Seq[Tweet]] = ???

  def getTweet[F[_]: Monad](tweetId: Int): F[Tweet]  = ???
  def getPopularTweets[F[_]: Monad](): F[Seq[Tweet]] = ???

  def tweetService[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = new org.http4s.dsl.Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "tweets" / "popular"       =>
        getPopularTweets().flatMap(Ok(_))
      case GET -> Root / "tweets" / IntVar(tweetId) =>
        getTweet(tweetId).flatMap(Ok(_))
    }
  }

  def services[F[_]: Sync] = tweetService[F] <+> helloWorldService[F]

  def httpApp[F[_]: Sync] = Router("/" -> helloWorldService[F], "/api" -> services).orNotFound

  def server[F[_]: Async] = EmberServerBuilder
    .default[F]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(httpApp)
    .build

  override def run(args: List[String]): IO[ExitCode] =
    server[IO]
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
