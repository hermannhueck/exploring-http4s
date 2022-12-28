package userguide._13httpclient

import cats.effect.{IO, IOApp}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client
import hutil.stringformat._

object Hello extends IOApp.Simple {

  val run: IO[Unit] = for {
    _ <- IO.println(dash80.green)
    _ <- EmberClientBuilder
           .default[IO]
           .build
           .use(client => printHello(client))
    _ <- IO.println(dash80.green)
  } yield ()

  def printHello(client: Client[IO]): IO[Unit] =
    client
      .expect[String]("https://httpbin.org/ip")
      .flatMap(IO.println)
}
