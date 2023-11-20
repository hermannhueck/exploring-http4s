package userguide._01quickstart

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def run(args: List[String]) =
    QuickstartServer.stream[IO].compile.drain.as(ExitCode.Success)
}
