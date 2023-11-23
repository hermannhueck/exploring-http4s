import cats.data.Kleisli
import cats.effect._
import org.typelevel.ci._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.std.Random
import fs2.Stream

import userguide._06b_servermiddleware._

// ===== Advanced =====

// ----- BodyCache -----

// Consumes and caches a request body so that it can be reused later. Usually reading the body twice is unsafe,
// this middleware ensures the body is always the same, at the cost of keeping it in memory.

// In this example we use a request body that always produces a different value once read:

import org.http4s.server.middleware.BodyCache

val bodyCacheService = BodyCache.httpRoutes(service).orNotFound

val randomRequest = Request[IO](Method.GET, uri"/doubleRead")
  .withEntity(
    Stream.eval(
      Random.scalaUtilRandom[IO].flatMap(_.nextInt).map(random => random.toString)
    )
  )

val bodyCacheClient = Client.fromHttpApp(bodyCacheService)

// /doubleRead reads the body twice, when using the middleware we see that both read values the same:

clnt
  .expect[String](randomRequest)
  .unsafeRunSync()
bodyCacheClient
  .expect[String](randomRequest)
  .unsafeRunSync()

// ----- BracketRequestResponse -----

// Brackets the handling of the request ensuring an action happens before the service handles the request (acquire)
// and another after the response is complete (release), the result of acquire is threaded to the underlying service.
// It's used to implement MaxActiveRequests and ConcurrentRequests. See BracketRequestResponse for more constructors.

import org.http4s.server.middleware.BracketRequestResponse
import org.http4s.ContextRoutes
import cats.effect.Ref

val ref =
  Ref[IO].of(0).unsafeRunSync()

val acquire: IO[Int]         = ref.updateAndGet(_ + 1)
val release: Int => IO[Unit] = _ => ref.update(_ - 1)

val bracketMiddleware =
  BracketRequestResponse
    .bracketRequestResponseRoutes[IO, Int](acquire)(release)

val bracketService = bracketMiddleware(
  ContextRoutes.of[Int, IO] { case GET -> Root / "ok" as n =>
    Ok(s"$n")
  }
).orNotFound

val bracketClient = Client.fromHttpApp(bracketService)

bracketClient
  .expect[String](okRequest)
  .unsafeRunSync()

ref
  .get
  .unsafeRunSync()

// ----- ChunkAggregator -----

// Consumes and caches a response body so that it can be reused later. Usually reading the body twice is unsafe,
// this middleware ensures the body is always the same, at the cost of keeping it in memory.

// Similarly to BodyRequest in this example we use a response body that always produces a different value:

import org.http4s.server.middleware.ChunkAggregator

def doubleBodyMiddleware(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.withBodyStream(resp.body ++ resp.body)
    case resp                    => resp
  }
}

val chunkAggregatorService = doubleBodyMiddleware(ChunkAggregator.httpRoutes(service)).orNotFound
val chunkAggregatorClient  = Client.fromHttpApp(chunkAggregatorService)

chunkAggregatorClient
  .expect[String](Request[IO](Method.POST, uri"/echo").withEntity("foo"))
  .map(e => s"$e == foofoo")
  .unsafeRunSync()

// ----- Jsonp -----

// Jsonp is a javascript technique to load json data without using XMLHttpRequest, which bypasses
// the same-origin security policy implemented in browsers. Jsonp usage is discouraged and can often be replaced
// with correct CORS configuration. This middleware has been deprecated as of 0.23.24.

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

// import org.http4s.server.middleware.Jsonp

val jsonRoutes = HttpRoutes.of[IO] { case GET -> Root / "json" =>
  Ok("""{"a": 1}""")
}

// val jsonService = Jsonp(callbackParam = "handleJson")(jsonRoutes).orNotFound
// val jsonClient  = Client.fromHttpApp(jsonService)
// val jsonRequest = Request[IO](Method.GET, uri"/json")

// jsonClient
//   .expect[String](jsonRequest)
//   .unsafeRunSync()

// ----- ContextMiddleware -----

// This middleware allows extracting context from a request and propagating it down to the routes.

import org.http4s.server.ContextMiddleware
import org.http4s.ContextRoutes
import cats.data.{Kleisli, OptionT}

// create a custom header
case class UserId(raw: String)
implicit val userIdHeader: Header[UserId, Header.Single] =
  Header.createRendered(ci"X-UserId", _.raw, s => Right(UserId(s)))

// middleware to read the user id from the request
val middleware = ContextMiddleware(
  Kleisli((r: Request[IO]) => OptionT.fromOption[IO](r.headers.get[UserId]))
)

// routes that expect a user id as context
val ctxRoutes = ContextRoutes.of[UserId, IO] { case GET -> Root / "ok" as userId =>
  Ok(s"hello ${userId.raw}")
}

val contextService = middleware(ctxRoutes).orNotFound
val contextClient  = Client.fromHttpApp(contextService)
val contextRequest = Request[IO](Method.GET, uri"/ok").putHeaders(UserId("Jack"))

contextClient
  .expect[String](contextRequest)
  .unsafeRunSync()
