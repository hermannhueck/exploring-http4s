import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Middleware -----

def myMiddle(service: HttpRoutes[IO], header: Header.ToRaw): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.putHeaders(header)
    case resp                    =>
      resp
  }
}

val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" =>
    BadRequest()
  case _                   =>
    Ok()
}

val goodRequest = Request[IO](Method.GET, uri"/")
val badRequest  = Request[IO](Method.GET, uri"/bad")

service.orNotFound(goodRequest).unsafeRunSync()
service.orNotFound(badRequest).unsafeRunSync()

val modifiedService = myMiddle(service, "SomeKey" -> "SomeValue");

modifiedService.orNotFound(goodRequest).unsafeRunSync()
modifiedService.orNotFound(badRequest).unsafeRunSync()

object MyMiddle {
  def addHeader(resp: Response[IO], header: Header.ToRaw) =
    resp match {
      case Status.Successful(resp) => resp.putHeaders(header)
      case resp                    => resp
    }

  def apply(service: HttpRoutes[IO], header: Header.ToRaw) =
    service.map(addHeader(_, header))
}

val newService = MyMiddle(service, "SomeKey" -> "SomeValue")

newService.orNotFound(goodRequest).unsafeRunSync()
newService.orNotFound(badRequest).unsafeRunSync()

// from the http4s library
// type AuthMiddleware[F[_], T] = Middleware[OptionT[F, *], AuthedRequest[F, T], Response[F], Request[F], Response[F]]

// ----- Composing Services with Middleware -----

val apiService = HttpRoutes.of[IO] { case GET -> Root / "api" =>
  Ok()
}

val anotherService = HttpRoutes.of[IO] { case GET -> Root / "another" =>
  Ok()
}

val aggregateService = apiService <+> MyMiddle(service <+> anotherService, "SomeKey" -> "SomeValue")

val apiRequest     = Request[IO](Method.GET, uri"/api")
val anotherRequest = Request[IO](Method.GET, uri"/another")

// runs through the middleware
aggregateService.orNotFound(goodRequest).unsafeRunSync().headers
aggregateService.orNotFound(anotherRequest).unsafeRunSync().headers
// doesn't run through the middleware
aggregateService.orNotFound(apiRequest).unsafeRunSync().headers
aggregateService.orNotFound(badRequest).unsafeRunSync().headers

// ----- Included Middleware -----

// X-Request-ID Middleware

import org.http4s.server.middleware.RequestId
import org.typelevel.ci._

val requestIdService = RequestId.httpRoutes(HttpRoutes.of[IO] { case req =>
  val reqId = req.headers.get(ci"X-Request-ID").fold("null")(_.head.value)
  // use request id to correlate logs with the request
  IO(println(s"request received, cid=$reqId")) *> Ok()
})
val responseIO       = requestIdService.orNotFound(goodRequest)

// Note: resp.attributes.lookup(RequestId.requestIdAttrKey) can also be used to lookup the request id
// extracted from the header, or the generated request id.

val resp = responseIO.unsafeRunSync()
resp.headers
resp.attributes
resp.attributes.lookup(RequestId.requestIdAttrKey)
