import cats.effect._
import cats.syntax.all._
import org.http4s._
import io.circe.syntax._
import io.circe.jawn._
import org.http4s.headers._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._
import cats.effect.std.{Console, Random}

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

val allRhinoFacts = List(
  "Rhinoceros horns are made from keratin, just like human fingernails.",
  "The rhino is the second largest land animal, after the elephant.",
  "The gestation period for a rhinoceros baby is almost 15 months."
)

@annotation.nowarn("msg=implicit numeric widening")
val service = HttpRoutes.of[IO] {
  case GET -> Root / "redirect"       => MovedPermanently(Location(uri"/ok"))
  case GET -> Root / "ok"             => Ok("👍")
  case r @ GET -> Root / "rhinoFacts" =>
    // show a rhino fact, try to not repeat a fact the user already has seen
    // the cookie is an array of integers, encoded in json
    val knownFacts: List[Int] = r
      .cookies
      .find(_.name == "knownRhinoFacts")
      .flatMap(cookie => decode[List[Int]](cookie.content).toOption)
      .toList
      .flatten

    // choose the index of a fact
    val factIndex =
      Random
        .scalaUtilRandom[IO]
        .flatMap(rng => rng.shuffleList(List.range(0, allRhinoFacts.size).filterNot(knownFacts.contains_)))
        .map(_.headOption)

    factIndex
      .flatMap { index =>
        val cookie   = index.map(_ :: knownFacts).getOrElse(knownFacts).asJson.noSpaces
        val response = index.flatMap(allRhinoFacts.get(_)).getOrElse("You know all the facts!")
        Ok(response).map(_.addCookie("knownRhinoFacts", cookie))
      }
}

val client = Client.fromHttpApp(service.orNotFound)

// ----- CookieJar -----

// Enhances a client to store and supply cookies. An in-memory implementation is provided,
// but it's also possible to supply your own method of persistence, see CookieJar.
// In this example the service will use the knowRhinoFacts cookie to track the facts that have been shown to the client.

import org.http4s.client.middleware.CookieJar

// the domain is necessary because cookies are tied to a domain
val factRequest = Request[IO](Method.GET, uri"http://example.com/rhinoFacts")

// without cookies the server will repeat facts
client
  .expect[String](factRequest)
  .flatMap(Console[IO].println)
  .replicateA(4)
  .void
  .unsafeRunSync()

// the server won't repeat facts because the client indicates what it already knows
CookieJar
  .impl(client)
  .flatMap { cl =>
    cl
      .expect[String](factRequest)
      .flatMap(Console[IO].println)
      .replicateA(4)
      .void
  }
  .unsafeRunSync()

// ----- DestinationAttribute -----

// This very simple middleware writes a value to the request attributes, which can be read at any point
// during the processing of the request, by other middleware down the line.

// In this example we create our own middleware that appends a header to the response.
// We use DestinationAttribute to provide the value.

import org.http4s.client.middleware.DestinationAttribute
import org.http4s.client.middleware.DestinationAttribute.Destination

def myMiddleware(cl: Client[IO]): Client[IO] = Client { req =>
  val destination = req.attributes.lookup(Destination).getOrElse("")
  cl.run(req).map(_.putHeaders("X-Destination" -> destination))
}

val mwClient = DestinationAttribute(myMiddleware(client), "example")

mwClient.run(Request[IO](Method.GET, uri"/ok")).use(_.headers.pure[IO]).unsafeRunSync()

// ----- FollowRedirects -----

// Allows a client to interpret redirect responses and follow them. See FollowRedirect for configuration.

import org.http4s.client.middleware.FollowRedirect

val redirectRequest = Request[IO](Method.GET, uri"/redirect")

client.status(redirectRequest).unsafeRunSync()
// res3: Status = Status(code = 301)
FollowRedirect(maxRedirects = 3)(client).status(redirectRequest).unsafeRunSync()

// ----- Logger, ResponseLogger, RequestLogger -----

// Log requests and responses. ResponseLogger logs the responses, RequestLogger logs the request, Logger logs both.

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

import org.http4s.client.middleware.Logger

val loggerClient = Logger[IO](
  logHeaders = false,
  logBody = true,
  logAction = Some((msg: String) => Console[IO].println(msg))
)(client)

loggerClient.expect[Unit](Request[IO](Method.GET, uri"/ok")).unsafeRunSync()

// ----- GZip -----

// Adds support for gzip compression. The client will indicate it can read gzip responses and if the server responds with gzip, the client will decode the response transparently.

import org.http4s.client.middleware.GZip
import org.http4s.server.middleware.{GZip => ServerGZip}
import org.http4s.client.middleware.ResponseLogger

val gzipService = ServerGZip(
  HttpRoutes.of[IO] { case GET -> Root / "long" => Ok("0123456789" * 5) }
).orNotFound

// the logger will print the bodies that are transferred
val clientWithoutGzip =
  ResponseLogger[IO](logHeaders = false, logBody = true, logAction = Some((msg: String) => Console[IO].println(msg)))(
    Client.fromHttpApp(gzipService)
  )

// this client will also log the bodies since it is backed by `clientWithoutGzip`
// the middleware will transform the server response so that it is uncompressed
// but the logger will allow us to print the body before it is uncompressed
val clientWithGzip = GZip()(clientWithoutGzip)
val longRequest    = Request[IO](Method.GET, uri"/long")

// without gzip in our client, nothing exciting happens
clientWithoutGzip
  .expect[String](longRequest)
  .unsafeRunSync()
// HTTP/1.1 200 OK body="01234567890123456789012345678901234567890123456789"

// with the middleware we can see that the original body is smaller and
// the response is decompressed transparently
clientWithGzip
  .expect[String](longRequest)
  .unsafeRunSync()
// HTTP/1.1 200 OK body="�30426153��4 �T���2"

// ----- Retry -----

// Allows a client to handle server errors by retrying requests. See Retry and RetryPolicy for ways of configuring the retry policy.

import org.http4s.client.middleware.{Retry, RetryPolicy}

// a service that fails the first three times it's called
val flakyService =
  Ref[IO].of(0).map { attempts =>
    HttpRoutes.of[IO] { case _ =>
      attempts
        .getAndUpdate(_ + 1)
        .flatMap(a => if (a < 3) ServiceUnavailable("not yet") else Ok("ok"))
    }
  }

val policy = RetryPolicy[IO](backoff = _ => Some(1.milli))

// without the middleware the call will fail
flakyService
  .flatMap { service =>
    val client = Client.fromHttpApp(service.orNotFound)
    client.expect[String](Request[IO](uri = uri"/")).attempt
  }
  .unsafeRunSync()

// with the middleware the call will succeed eventually
flakyService
  .flatMap { service =>
    val client      = Client.fromHttpApp(service.orNotFound)
    val retryClient = Retry(policy)(client)
    retryClient.expect[String](Request[IO](uri = uri"/"))
  }
  .unsafeRunSync()

// ----- UnixSocket -----

// Unix domain sockets are an operating system feature which allows communication between processes
// while not needing to use the network.

// This middleware allows a client to make requests to a domain socket.

import fs2.io.file._
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s.client.middleware.UnixSocket
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder

val localSocket = Files[IO]
  .tempFile(None, "", ".sock", None)
  .map(path => UnixSocketAddress(path.toString))

def server(socket: UnixSocketAddress) = EmberServerBuilder
  .default[IO]
  .withUnixSocketConfig(UnixSockets[IO], socket) // bind to a domain socket
  .withHttpApp(service.orNotFound)
  .withShutdownTimeout(1.second)
  .build
  .evalTap(_ => IO.sleep(4.seconds))

def client(socket: UnixSocketAddress) = EmberClientBuilder
  .default[IO]
  .build
  .map(UnixSocket[IO](socket)) // apply the middleware

// localSocket
//   .flatMap(socket => server(socket) *> client(socket))
//   .use(cl => cl.status(Request[IO](uri = uri"/ok")))
//   .unsafeRunSync()
