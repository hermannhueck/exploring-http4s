import cats.effect._
import cats.syntax.all._
import org.typelevel.ci._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import scala.concurrent.duration._
import cats.effect.std.Console

import userguide._06b_servermiddleware._

// ===== Headers =====

// ----- Caching -----

// This middleware adds response headers so that clients know how to cache a response.
// It performs no server-side caching. Below is one example of usage, see Caching for more methods.

import org.http4s.server.middleware.Caching

val cacheService = Caching
  .cache(
    3.hours,
    isPublic = Left(CacheDirective.public),
    methodToSetOn = _ == Method.GET,
    statusToSetOn = _.isSuccess,
    service
  )
  .orNotFound

val cacheClient = Client.fromHttpApp(cacheService)

cacheClient
  .run(okRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()
cacheClient
  .run(badRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()
cacheClient
  .run(postRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()

// ----- Date -----

// Adds the current date to the response.

import org.http4s.server.middleware.Date

val dateService = Date.httpRoutes(service).orNotFound
val dateClient  = Client.fromHttpApp(dateService)

dateClient
  .run(okRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()

// ----- HeaderEcho -----

// Adds headers included in the request to the response.

import org.http4s.server.middleware.HeaderEcho

val echoService = HeaderEcho.httpRoutes(echoHeadersWhen = _ => true)(service).orNotFound
val echoClient  = Client.fromHttpApp(echoService)

echoClient
  .run(okRequest.putHeaders("Hello" -> "hi"))
  .use(_.headers.pure[IO])
  .unsafeRunSync()

// ----- ResponseTiming -----

// Sets response header with the request duration.

import org.http4s.server.middleware.ResponseTiming

val timingService = ResponseTiming(service.orNotFound)
val timingClient  = Client.fromHttpApp(timingService)

timingClient
  .run(okRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()

// ----- RequestId -----

// Use the RequestId middleware to automatically generate a X-Request-ID header for a request, if one wasn't supplied.
// Adds a X-Request-ID header to the response with the id generated or supplied as part of the request.
// This heroku guide gives a brief explanation as to why this header is useful.

import org.http4s.server.middleware.RequestId

val requestIdService = RequestId.httpRoutes(HttpRoutes.of[IO] { case req =>
  val reqId = req.headers.get(ci"X-Request-ID").fold("null")(_.head.value)
  // use request id to correlate logs with the request
  Console[IO].println(s"request received, cid=$reqId") *> Ok()
})

val requestIdClient = Client.fromHttpApp(requestIdService.orNotFound)

// Note: req.attributes.lookup(RequestId.requestIdAttrKey) can also be used to lookup the request id
// extracted from the header, or the generated request id.

requestIdClient
  .run(okRequest)
  .use(resp => (resp.headers, resp.attributes.lookup(RequestId.requestIdAttrKey)).pure[IO])
  .unsafeRunSync()

// ----- StaticHeaders -----

// Adds static headers to the response.

import org.http4s.server.middleware.StaticHeaders

val staticHeadersService = StaticHeaders(Headers("X-Hello" -> "hi"))(service).orNotFound
val staticHeaderClient   = Client.fromHttpApp(staticHeadersService)

staticHeaderClient
  .run(okRequest)
  .use(_.headers.pure[IO])
  .unsafeRunSync()
