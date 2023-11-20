package userguide._13httpclient

import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s.server.middleware.Logger

object HttpServer extends IOApp.Simple {

// import cats.effect.unsafe.IORuntime
// implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  val app = HttpRoutes
    .of[IO] { case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
    }
    .orNotFound

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val finalHttpApp = Logger.httpApp(true, true)(app)

  val run: IO[Unit] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(finalHttpApp)
    .build
    .use(_ => IO.never)
    .void

// val shutdown = server.allocated.unsafeRunSync()._2
}
