package userguide._01quickstart

import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import fs2.io.net.Network

object QuickstartServer {

  import org.typelevel.log4cats.LoggerFactory

  def stream[F[_]: Async: Network: LoggerFactory]: Stream[F, Nothing] = {
    for {
      client       <- Stream.resource(EmberClientBuilder.default[F].build)
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg       = Jokes.impl[F](client)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract segments not checked
      // in the underlying routes.
      httpApp      = (
                       QuickstartRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
                         QuickstartRoutes.jokeRoutes[F](jokeAlg)
                     ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- Stream.resource(
                    EmberServerBuilder
                      .default[F]
                      .withHost(ipv4"0.0.0.0")
                      .withPort(port"8080")
                      .withHttpApp(finalHttpApp)
                      .build >>
                      Resource.eval(Async[F].never)
                  )
    } yield exitCode
  }.drain
}
