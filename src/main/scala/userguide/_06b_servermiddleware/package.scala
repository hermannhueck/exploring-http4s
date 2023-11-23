package userguide

import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._
import cats.effect.std.Random
import fs2.Stream

package object _06b_servermiddleware {

  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

  val service = HttpRoutes.of[IO] {
    case GET -> Root / "bad"                                       => BadRequest()
    case GET -> Root / "ok"                                        => Ok()
    case r @ POST -> Root / "post"                                 => r.as[Unit] >> Ok()
    case r @ POST -> Root / "echo"                                 => r.as[String].flatMap(Ok(_))
    case GET -> Root / "b" / "c"                                   => Ok()
    case POST -> Root / "queryForm" :? NameQueryParamMatcher(name) => Ok(s"hello $name")
    case GET -> Root / "wait"                                      => IO.sleep(10.millis) >> Ok()
    case GET -> Root / "boom"                                      => IO.raiseError(new RuntimeException("boom!"))
    case r @ POST -> Root / "reverse"                              => r.as[String].flatMap(s => Ok(s.reverse))
    case GET -> Root / "forever"                                   =>
      IO(
        Response[IO](headers = Headers("hello" -> "hi"))
          .withEntity(Stream.constant("a").covary[IO])
      )
    case r @ GET -> Root / "doubleRead"                            =>
      (r.as[String], r.as[String])
        .flatMapN((a, b) => Ok(s"$a == $b"))
    case GET -> Root / "random"                                    =>
      Random
        .scalaUtilRandom[IO]
        .flatMap(_.nextInt)
        .flatMap(random => Ok(random.toString))
  }

  val okRequest      = Request[IO](Method.GET, uri"/ok")
  val badRequest     = Request[IO](Method.GET, uri"/bad")
  val postRequest    = Request[IO](Method.POST, uri"/post")
  val waitRequest    = Request[IO](Method.GET, uri"/wait")
  val boomRequest    = Request[IO](Method.GET, uri"/boom")
  val reverseRequest = Request[IO](Method.POST, uri"/reverse")

  val clnt = Client.fromHttpApp(service.orNotFound)
}
