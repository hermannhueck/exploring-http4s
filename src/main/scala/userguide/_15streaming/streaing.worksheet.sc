import scala.concurrent.duration._

import fs2.Stream

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// An infinite stream of the periodic elapsed time
val seconds: Stream[IO, FiniteDuration] =
  Stream.awakeEvery[IO](1.second)

val routes: HttpRoutes[IO] =
  HttpRoutes.of[IO] { case GET -> Root / "seconds" =>
    Ok(seconds.map(_.toString)) // `map` `toString` because there's no `EntityEncoder` for `Duration`
  }

val request: Request[IO] =
  Request(method = GET, uri = uri"/seconds")

val response: Response[IO] =
  routes
    .orNotFound
    .run(request)
    .unsafeRunSync()

// runs infinitely
//
// response
//   .body
//   .evalMap(IO.println)
//   .compile
//   .drain
//   .unsafeRunSync()
