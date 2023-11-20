package userguide._16jsonhandling

import cats.effect._

import com.comcast.ip4s._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._

import Domain._
import org.http4s.server.Server

object HelloServerApp extends IOApp.Simple {

  implicit val decoder: EntityDecoder[IO, User] =
    jsonOf[IO, User]

  val jsonApp: HttpApp[IO] =
    HttpRoutes
      .of[IO] { case req @ POST -> Root / "hello" =>
        println(s"Received request: $req")
        for {
          // Decode a User request
          user <- req.as[User]
          // Encode a hello response
          resp <- Ok(Hello(user.name).asJson)
        } yield (resp)
      }
      .orNotFound

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def server(app: HttpApp[IO]): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(app)
      .build

  val run: IO[Unit] =
    server(jsonApp)
      .use(_ => IO.never)
      .void
}
