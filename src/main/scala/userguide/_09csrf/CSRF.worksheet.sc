import org.typelevel.ci.CIString
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.middleware._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Basic Configuration -----

val service = HttpRoutes.of[IO] { case _ =>
  Ok()
}

val request = Request[IO](Method.GET, uri"/")

service
  .orNotFound(request)
  .unsafeRunSync()
  .ensuring { _.status == Status.Ok }

val cookieName =
  "csrf-token"

val key =
  CSRF
    .generateSigningKey[IO]()
    .unsafeRunSync()

val defaultOriginCheck: Request[IO] => Boolean =
  request =>
    CSRF
      .defaultOriginCheck[IO](request, "localhost", Uri.Scheme.http, None)

val csrfBuilder: CSRF.CSRFBuilder[IO, IO] =
  CSRF[IO, IO](key, defaultOriginCheck)

val csrf: CSRF[IO, IO] =
  csrfBuilder
    .withCookieName(cookieName)
    .withCookieDomain(Some("localhost"))
    .withCookiePath(Some("/"))
    .build

val dummyRequest: Request[IO] =
  Request[IO](method = Method.GET).putHeaders("Origin" -> "http://localhost")

val middleware =
  csrf
    .validate()

val resp =
  middleware(service.orNotFound)(dummyRequest)
    .unsafeRunSync()
    .ensuring { r =>
      r.status == Status.Ok &&
      r.headers.get(CIString("Set-Cookie")).isDefined &&
      r.headers.get(CIString("Set-Cookie")).get.head.value.startsWith("csrf-token=")
    }

val cookie = resp.cookies.head

val dummyPostRequest: Request[IO] =
  Request[IO](method = Method.POST)
    .putHeaders(
      "Origin"       -> "http://localhost",
      "X-Csrf-Token" -> cookie.content
    )
    .addCookie(RequestCookie(cookie.name, cookie.content))

val validateResp =
  middleware(service.orNotFound)(dummyPostRequest)
    .unsafeRunSync()
    .ensuring { r =>
      r.status == Status.Ok &&
      r.cookies.nonEmpty // &&
    // r.cookies.head.content == cookie.content
    }

validateResp
  .cookies
  .head
  .ensuring { c =>
    c.name == "csrf-token" &&
    c.domain == Some("localhost") &&
    c.path == Some("/") &&
    c.httpOnly
  }
