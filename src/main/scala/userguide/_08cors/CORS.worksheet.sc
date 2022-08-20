import cats.data.NonEmptyList
import org.typelevel.ci.CIString
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

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
  .ensuring {
    _.status == Status.Ok
  }

import org.http4s.server.middleware._

val corsService = CORS.policy.withAllowOriginAll(service)

corsService
  .orNotFound(request)
  .unsafeRunSync()
  .ensuring {
    _.status == Status.Ok
  }

val corsRequest = request.putHeaders("Origin" -> "https://somewhere.com")

corsService
  .orNotFound(corsRequest)
  .unsafeRunSync()
  .ensuring {
    _.status == Status.Ok
  }
  .ensuring { response =>
    val headerKey = CIString("Access-Control-Allow-Origin")
    response.headers.get(headerKey) == Some(NonEmptyList.of(Header.Raw(headerKey, "*")))
  }

// ----- Configuration -----

val googleGet = Request[IO](Method.GET, uri"/", headers = Headers("Origin" -> "https://google.com"))

val yahooPut = Request[IO](Method.PUT, uri"/", headers = Headers("Origin" -> "https://yahoo.com"))

val duckPost = Request[IO](Method.POST, uri"/", headers = Headers("Origin" -> "https://duckduckgo.com"))

import scala.concurrent.duration._

val corsMethodSvc =
  CORS
    .policy
    .withAllowOriginAll
    .withAllowMethodsIn(Set(Method.GET, Method.POST))
    .withAllowCredentials(false)
    .withMaxAge(1.day)
    .apply(service)

corsMethodSvc
  .orNotFound(googleGet)
  .unsafeRunSync()
  .ensuring { response =>
    val headerKey = CIString("Access-Control-Allow-Origin")
    response.status == Status.Ok &&
    response.headers.get(headerKey) == Some(NonEmptyList.of(Header.Raw(headerKey, "*")))
  }

// !!! This invocation should not return a CORS header, but it does !!!
corsMethodSvc
  .orNotFound(yahooPut)
  .unsafeRunSync()
  .ensuring {
    _.status == Status.Ok
  }
// !!! This invocation should not return a CORS header, but it does !!!
// .ensuring { response =>
//   val headerKey = CIString("Access-Control-Allow-Origin")
//   response.status == Status.Ok &&
//   response.headers.get(headerKey) == Some(NonEmptyList.of(Header.Raw(headerKey, "*")))
// }

corsMethodSvc
  .orNotFound(duckPost)
  .unsafeRunSync()
  .ensuring { response =>
    val headerKey = CIString("Access-Control-Allow-Origin")
    response.status == Status.Ok &&
    response.headers.get(headerKey) == Some(NonEmptyList.of(Header.Raw(headerKey, "*")))
  }

import org.http4s.headers.Origin

val corsOriginSvc =
  CORS
    .policy
    .withAllowOriginHost(
      Set(
        Origin.Host(Uri.Scheme.https, Uri.RegName("yahoo.com"), None),
        Origin.Host(Uri.Scheme.https, Uri.RegName("duckduckgo.com"), None)
      )
    )
    .withAllowCredentials(false)
    .withMaxAge(1.day)
    .apply(service)

corsOriginSvc
  .orNotFound(googleGet)
  .unsafeRunSync()
  .ensuring { response =>
    val headerKey = CIString("Vary")
    response.status == Status.Ok &&
    response.headers.get(headerKey) == Some(NonEmptyList.of(Header.Raw(headerKey, "Origin")))
  }

val resp02 =
  corsOriginSvc
    .orNotFound(yahooPut)
    .unsafeRunSync()
    .ensuring { response =>
      val key1 = CIString("Access-Control-Allow-Origin")
      val key2 = CIString("Vary")
      response.status == Status.Ok &&
      response.headers.get(key1) == Some(NonEmptyList.of(Header.Raw(key1, "https://yahoo.com"))) &&
      response.headers.get(key2) == Some(NonEmptyList.of(Header.Raw(key2, "Origin")))
    }

import munit.Assertions._

assertEquals(resp02.status, Status.Ok)
assertEquals(resp02.headers.get(CIString("Access-Control-Allow-Origin")).get.head.value, "https://yahoo.com")
assertEquals(resp02.headers.get(CIString("Vary")).get.head.value, "Origin")

val resp03 =
  corsOriginSvc
    .orNotFound(yahooPut)
    .unsafeRunSync()

assertEquals(resp03.status, Status.Ok)
assertEquals(resp03.headers.get(CIString("Access-Control-Allow-Origin")).get.head.value, "https://yahoo.com")
assertEquals(resp03.headers.get(CIString("Vary")).get.head.value, "Origin")
