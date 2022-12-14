package userguide._01quickstart

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  def run(args: List[String]) =
    QuickstartServer.stream[IO].compile.drain.as(ExitCode.Success)
}
