import org.typelevel.ci.CIString
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import cats.effect.IO

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Basic Configuration -----

val service = HttpRoutes.of[IO] { case _ =>
  Ok("ok")
}

val request = Request[IO](Method.GET, uri"/")

val responseOK =
  service
    .orNotFound(request)
    .unsafeRunSync()
    .ensuring { _.status == Status.Ok }

responseOK.headers

import org.http4s.server.middleware._

val hstsService = HSTS(service)

val responseHSTS = hstsService
  .orNotFound(request)
  .unsafeRunSync()

responseHSTS
  .headers
  .ensuring { headers =>
    headers.get(CIString("Strict-Transport-Security")).isDefined
  }

import org.http4s.headers._
import scala.concurrent.duration._

val maxAge     = 30.days
val hstsHeader = `Strict-Transport-Security`
  .unsafeFromDuration(maxAge, includeSubDomains = true, preload = true)

val hstsServiceCustom = HSTS(service, hstsHeader)

val responseCustom =
  hstsServiceCustom
    .orNotFound(request)
    .unsafeRunSync()
    .ensuring { _.status == Status.Ok }

responseCustom
  .headers
  .ensuring { headers =>
    headers.get(CIString("Strict-Transport-Security")).isDefined
  }

val hstsHeaderReceivedValue =
  responseCustom
    .headers
    .get(CIString("Strict-Transport-Security"))
    .get
    .head
    .value

hstsHeaderReceivedValue
  .split(";")
  .toList
  .map(_.trim)
  .ensuring { l =>
    l.contains("max-age=2592000") &&
    l.contains("includeSubDomains") &&
    l.contains("preload") &&
    l.head.split("=")(1).toLong == maxAge.toSeconds
  }
maxAge.toSeconds
