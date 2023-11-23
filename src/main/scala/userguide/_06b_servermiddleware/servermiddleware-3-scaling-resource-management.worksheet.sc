import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.Client
import scala.concurrent.duration._
import cats.effect.std.Console

import userguide._06b_servermiddleware._

// ===== Scaling and resource management =====

// ----- ConcurrentRequests -----

// React to requests being accepted and completed, could be used for metrics.

import org.http4s.server.middleware.ConcurrentRequests
import org.http4s.server.{ContextMiddleware, HttpMiddleware}
import org.http4s.ContextRequest
import cats.data.Kleisli

// a utility that drops the context from the request, since our service expects
// a plain request
def dropContext[A](middleware: ContextMiddleware[IO, A]): HttpMiddleware[IO] =
  routes => middleware(Kleisli((c: ContextRequest[IO, A]) => routes(c.req)))

val concurrentService =
  ConcurrentRequests
    .route[IO](
      onIncrement = total => Console[IO].println(s"someone comes to town, total=$total"),
      onDecrement = total => Console[IO].println(s"someone leaves town, total=$total")
    )
    .map((middle: ContextMiddleware[IO, Long]) => dropContext(middle)(service).orNotFound)

val concurrentClient =
  concurrentService
    .map(Client.fromHttpApp[IO])

concurrentClient
  .flatMap(cl => List.fill(3)(waitRequest).parTraverse(req => cl.expect[Unit](req)))
  .void
  .unsafeRunSync()

// ----- EntityLimiter -----

// Ensures the request body is under a specific length. It does so by inspecting the body,
// not by simply checking Content-Length (which could be spoofed). This could be useful for file uploads,
// or to prevent attacks that exploit a service that loads the whole body into memory.
// Note that many EntityDecoders are susceptible to this form of attack:
// the String entity decoder will read the complete value into memory,
// while a json entity decoder might build the full AST before attempting to decode.
// For this reason it's advisable to apply this middleware unless something else,
// like a reverse proxy, is applying this limit.

import org.http4s.server.middleware.EntityLimiter

val limiterService = EntityLimiter.httpApp(service.orNotFound, limit = 16)
val limiterClient  = Client.fromHttpApp(limiterService)
val smallRequest   = postRequest.withEntity("*" * 15)
val bigRequest     = postRequest.withEntity("*" * 16)

limiterClient
  .status(smallRequest)
  .unsafeRunSync()

limiterClient
  .status(bigRequest)
  .attempt
  .unsafeRunSync()

// ----- MaxActiveRequests -----

// Limit the number of active requests by rejecting requests over a certain limit.
// This can be useful to ensure that your service remains responsive during high loads.

import org.http4s.server.middleware.MaxActiveRequests

// creating the middleware is effectful
val maxService = MaxActiveRequests
  .forHttpApp[IO](maxActive = 2)
  .map(middleware => middleware(service.orNotFound))

val maxClient = maxService.map(Client.fromHttpApp[IO])

// Some requests will fail if the limit is reached:

maxClient
  .flatMap(cl => List.fill(5)(waitRequest).parTraverse(req => cl.status(req)))
  .unsafeRunSync()

// ----- Throttle -----

// Reject requests that exceed a given rate. An in-memory implementation of a TokenBucket - which refills at a given rate -
// is provided, but other strategies can be used. Like MaxActiveRequest this can be used prevent a service from being affect by high load.

import org.http4s.server.middleware.Throttle

// creating the middleware is effectful because of the default token bucket
val throttleService = Throttle.httpApp[IO](
  amount = 1,
  per = 10.milliseconds
)(service.orNotFound)

val throttleClient = throttleService.map(Client.fromHttpApp[IO])

// We'll submit request every 5 ms and refill a token every 10 ms:

throttleClient
  .flatMap(cl => List.fill(5)(okRequest).traverse(req => IO.sleep(5.millis) >> cl.status(req)))
  .unsafeRunSync()

// ----- Timeout -----

// Limits how long the underlying service takes to respond. The service is cancelled,
// if there are uncancelable effects they are completed and only then is the response returned.

import org.http4s.server.middleware.Timeout

val timeoutService = Timeout.httpApp[IO](timeout = 5.milliseconds)(service.orNotFound)
val timeoutClient  = Client.fromHttpApp(timeoutService)

// /wait takes 10 ms to finish so it's cancelled:

timeoutClient
  .status(waitRequest)
  .timed
  .unsafeRunSync()
