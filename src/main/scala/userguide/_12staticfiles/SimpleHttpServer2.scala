package userguide._12staticfiles

import cats._
import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s._
import org.http4s.server._
import org.http4s.server.staticcontent._
import org.http4s.dsl._
import org.http4s.dsl.io._

object SimpleHttpServer2 extends IOApp {

  val indexServiceIO: HttpRoutes[IO] = HttpRoutes.of[IO] { case request @ GET -> Root / "index.html" =>
    StaticFile
      .fromPath(fs2.io.file.Path("assets/index.html"), Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
  }

  def indexService[F[_]: Async]: HttpRoutes[F] = HttpRoutes.of[F] { case request @ GET -> Root / "index.html" =>
    val dsl = new Http4sDsl[F] {}
    import dsl._
    StaticFile
      .fromPath[F](fs2.io.file.Path("assets/index.html"), Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
  }

  def anotherService[F[_]: Monad]: HttpRoutes[F] = HttpRoutes.of[F] { case _ =>
    val dsl = new Http4sDsl[F] {}
    import dsl._
    Ok("HELLO WORLD !!!\n")
  }

  def httpApp[F[_]: Async]: HttpApp[F] =
    Router[F](
      "/"      -> indexService,
      "assets" -> fileService(FileService.Config("./assets")),
      ""       -> anotherService
    ).orNotFound

  val app: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build

  override def run(args: List[String]): IO[ExitCode] =
    app.use(_ => IO.never).as(ExitCode.Success)
}
