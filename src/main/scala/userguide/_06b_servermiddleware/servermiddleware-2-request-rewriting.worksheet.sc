import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.Client

import userguide._06b_servermiddleware._

// ===== Request rewriting =====

// ----- AutoSlash -----

// Removes a trailing slash from the requested url so that requests with trailing slash map to the route without.

import org.http4s.server.middleware.AutoSlash

val autoSlashService = AutoSlash(service).orNotFound
val autoSlashClient  = Client.fromHttpApp(autoSlashService)
val okWithSlash      = Request[IO](Method.GET, uri"/ok/")

// without the middleware the request with trailing slash fails
clnt
  .status(okRequest)
  .unsafeRunSync()
clnt
  .status(okWithSlash)
  .unsafeRunSync()

// with the middleware both work
autoSlashClient
  .status(okRequest)
  .unsafeRunSync()
autoSlashClient
  .status(okWithSlash)
  .unsafeRunSync()

// ----- DefaultHead -----

// Provides a naive implementation of a HEAD request for any GET routes. The response has the same headers but no body.
// An attempt is made to interrupt the process of generating the body.

import org.http4s.server.middleware.DefaultHead

val headService = DefaultHead(service).orNotFound
val headClient  = Client.fromHttpApp(headService)

// /forever has an infinite body but the HEAD request terminates and includes the headers:

headClient
  .status(Request[IO](Method.HEAD, uri"/forever"))
  .unsafeRunSync()
headClient
  .run(Request[IO](Method.HEAD, uri"/forever"))
  .use(_.headers.pure[IO])
  .unsafeRunSync()

// ----- HttpMethodOverrider -----

// Allows a client to "disguise" the http verb of a request by indicating the desired verb somewhere else in the request.

import org.http4s.server.middleware.HttpMethodOverrider
import org.http4s.server.middleware.HttpMethodOverrider.{HttpMethodOverriderConfig, QueryOverrideStrategy}

val overrideService = HttpMethodOverrider(
  service,
  HttpMethodOverriderConfig(
    QueryOverrideStrategy(paramName = "realMethod"),
    Set(Method.GET)
  )
).orNotFound

val overrideClient  = Client.fromHttpApp(overrideService)
val overrideRequest = Request[IO](Method.GET, uri"/post?realMethod=POST")

clnt
  .status(overrideRequest)
  .unsafeRunSync()
overrideClient
  .status(overrideRequest)
  .unsafeRunSync()

// ----- HttpsRedirect -----

// Redirects requests to https when the X-Forwarded-Proto header is http. This header is usually provided by a load-balancer to indicate which protocol the client used.

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

import org.http4s.server.middleware.HttpsRedirect

val httpsRedirectService = HttpsRedirect(service).orNotFound
val httpsRedirectClient  = Client.fromHttpApp(httpsRedirectService)
val httpRequest          = okRequest
  .putHeaders("Host" -> "example.com", "X-Forwarded-Proto" -> "http")

httpsRedirectClient
  .run(httpRequest)
  .use(r => (r.headers, r.status).pure[IO])
  .unsafeRunSync()

// ----- TranslateUri -----

// Removes a prefix from the path of the requested url.

import org.http4s.server.middleware.TranslateUri

val translateService = TranslateUri(prefix = "a")(service).orNotFound
val translateRequest = Request[IO](Method.GET, uri"/a/b/c")
val translateClient  = Client.fromHttpApp(translateService)

// The following is successful even though /b/c is defined, and not /a/b/c:

translateClient
  .status(translateRequest)
  .unsafeRunSync()

// ----- UrlFormLifter -----

// Transform x-www-form-urlencoded parameters into query parameters.

import org.http4s.server.middleware.UrlFormLifter
import org.http4s.UrlForm

val urlFormService = UrlFormLifter.httpApp(service.orNotFound)
val urlFormClient  = Client.fromHttpApp(urlFormService)

val formRequest = Request[IO](Method.POST, uri"/queryForm")
  .withEntity(UrlForm.single("name", "John"))

// Even though the /queryForm route takes query parameters, the form request works:

urlFormClient
  .expect[String](formRequest)
  .unsafeRunSync()
