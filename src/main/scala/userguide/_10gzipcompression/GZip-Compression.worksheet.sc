import org.typelevel.ci.CIString
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Basic Configuration -----

val service = HttpRoutes.of[IO] { case _ =>
  Ok("I repeat myself when I'm under stress. " * 3)
}

val request = Request[IO](Method.GET, uri"/")

val response =
  service
    .orNotFound(request)
    .unsafeRunSync()
    .ensuring { _.status == Status.Ok }

val body =
  response
    .as[String]
    .unsafeRunSync()
    .ensuring(_.length == 117)
body.length

import org.http4s.server.middleware._

val serviceZip = GZip(service)

val respNormal =
  serviceZip
    .orNotFound(request)
    .unsafeRunSync()
    .ensuring { _.status == Status.Ok }

val bodyNormal =
  respNormal
    .as[String]
    .unsafeRunSync()
    .ensuring(_.length == 117)
bodyNormal.length

val requestZip = request.putHeaders("Accept-Encoding" -> "gzip")

val respZip =
  serviceZip
    .orNotFound(requestZip)
    .unsafeRunSync()
    .ensuring { r =>
      r.status == Status.Ok &&
      r.headers.get(CIString("Content-Encoding")).isDefined &&
      r.headers.get(CIString("Content-Encoding")).get.head.value == "gzip"
    }

val bodyZip =
  respZip
    .as[String]
    .unsafeRunSync()
    .ensuring(_.length < 117)
bodyZip.length
