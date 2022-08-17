package userguide

import org.http4s._
import cats.effect.IO
import cats.effect.unsafe.IORuntime

package object util {

  def runWithRequest(routes: HttpRoutes[IO], request: Request[IO])(implicit runtime: IORuntime): Response[IO] = {
    routes                 // : HttpRoutes[IO] ( = Http[OptionT[IO, *], IO] )
      .orNotFound(request) // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
      .unsafeRunSync()     // : Response[IO]
  }

  def evalBodyAsString(response: Response[IO])(implicit runtime: IORuntime): String =
    response
      .body                          // : EntityBody[IO] ( = Stream[IO, Byte] )
      .through(fs2.text.utf8.decode) // : Stream[IO, String]
      .compile                       // : Stream.CompileOps[IO, IO, String]
      .toVector                      // : IO[Vector[String]]
      .unsafeRunSync()               // : Vector[String]
      .head                          // : String
}
