package userguide._12staticfiles

import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.staticcontent._

object SimpleHttpServer extends IOApp {

  val app: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(fileService[IO](FileService.Config(".")).orNotFound)
      .build

  override def run(args: List[String]): IO[ExitCode] =
    app.use(_ => IO.never).as(ExitCode.Success)
}
