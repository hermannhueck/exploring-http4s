import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Creating the Client -----

import org.http4s.client._

// @annotation.nowarn("cat=unused")
// EmberClientBuilder.default[IO].build.use { client =>
//   // use `client` here and return an `IO`.
//   // the client will be acquired and shut down
//   // automatically each time the `IO` is run.
//   IO.unit
// }

// For the remainder of this tutorial, we'll use an alternate client backend built on the standard java.net library client.
// Unlike the ember client, it does not need to be shut down. Like the ember-client, and any other http4s backend,
// it presents the exact same Client interface!

// It uses blocking IO and is less suited for production, but it is highly useful in a REPL:

import java.util.concurrent._

val blockingPool           = Executors.newFixedThreadPool(5)
val httpClient: Client[IO] = JavaNetClientBuilder[IO].create

import cats._, cats.effect._, cats.implicits._
import org.http4s.Uri

// Describing a call

val helloJames             =
  httpClient.expect[String]("http://localhost:8080/hello/James")
val helloEmber: IO[String] =
  httpClient.expect[String]("http://localhost:8080/hello/Ember")

// Running the call

helloJames.unsafeRunSync()
helloEmber.unsafeRunSync()

// Describing 4 calls

def hello(name: String): IO[String] = {
  val target = uri"http://localhost:8080/hello/" / name
  httpClient.expect[String](target)
}

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")

val greetingList: IO[Vector[String]] = people.parTraverse(hello)

val greetingsStringEffect = greetingList.map(_.mkString("\n"))

// Running the calls

// requires running server
greetingsStringEffect.unsafeRunSync()

// Constructing a URI

uri"https://my-awesome-service.com/foo/bar?wow=yeah"

val validUri   = "https://my-awesome-service.com/foo/bar?wow=yeah"
val invalidUri = "yeah whatever"

// type ParseResult[+A] = Either[ParseFailure, A]
val uri: ParseResult[Uri]          = Uri.fromString(validUri)
val parseFailure: ParseResult[Uri] = Uri.fromString(invalidUri)

// You can also build up a URI incrementally, e.g.:

val baseUri: Uri   = uri"http://foo.com"
val withPath: Uri  = baseUri.withPath(path"/bar/baz")
val withQuery: Uri = withPath.withQueryParam("hello", "world")

// ----- Client Middleware -----

def mid(f: Int => String): Int => String = in => {
  // here, `in` is the input originally passed to the function
  // we can decide to pass it to `f`, or modify it first. We'll change it for the example.
  val resultOfF = f(in + 1)

  // Now, `resultOfF` is the result of the function applied with the modified result.
  // We can return it verbatim or _also_ modify it first! We could even ignore it.
  // Here, we'll use both results - the one we got from the original call (f(in)) and the customized one (f(in + 1)).
  s"${f(in)} is the original result, but $resultOfF's input was modified!"
}

val f1: Int => String = _.toString
val f2: Int => String = mid(f1)

val x = 10
f1(x)
f2(x)

// ----- Examples -----

// Send a GET request, treating the response as a string

// requires running server
// httpClient
//   .expect[String](uri"http://localhost:8080/hello/James")
//   .unsafeRunSync()

httpClient
  .expect[String](uri"https://httpbin.org/get")
  .unsafeRunSync()

import cats.effect.MonadCancelThrow
import org.typelevel.ci._

def addTestHeader[F[_]: MonadCancelThrow](underlying: Client[F]): Client[F] = Client[F] { req =>
  underlying
    .run(
      req.withHeaders(Header.Raw(ci"X-Test-Request", "test"))
    )
    .map(
      _.withHeaders(Header.Raw(ci"X-Test-Response", "test"))
    )
}

val testClient = addTestHeader(httpClient)

testClient
  .expect[String](uri"https://httpbin.org/get")
  .unsafeRunSync()

import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType

val request: Request[IO] = GET(
  uri"https://my-lovely-api.com/",
  Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
  Accept(MediaType.application.json)
)

import org.typelevel.ci.CIString
import munit.Assertions._

val headers =
  request.headers.headers.map(hr => hr.name.toString -> hr.value).toMap
assertEquals(headers.get("Authorization"), Some("Bearer open sesame"))
assertEquals(headers.get("Accept"), Some("application/json"))

assertEquals(request.headers.get(CIString("Authorization")).get.head.value, "Bearer open sesame")
assertEquals(request.headers.get(CIString("Accept")).get.head.value, "application/json")

httpClient.expect[String](request)

// Post a form, decoding the JSON response to a case class

import org.http4s.circe._
import io.circe.generic.auto._

case class AuthResponse(access_token: String)

implicit val authResponseEntityDecoder: EntityDecoder[IO, AuthResponse] =
  jsonOf[IO, AuthResponse]

val postRequest = POST(
  UrlForm(
    "grant_type"    -> "client_credentials",
    "client_id"     -> "my-awesome-client",
    "client_secret" -> "s3cr3t"
  ),
  uri"https://my-lovely-api.com/oauth2/token"
)

val authRespose: IO[AuthResponse] =
  httpClient.expect[AuthResponse](postRequest)

// ----- Body decoding / encoding -----

val endpoint = uri"http://localhost:8080/hellox/Ember"
val result   = httpClient.get[Either[String, String]](endpoint) {
  case Status.Successful(response) =>
    response
      .attemptAs[String]
      .leftMap(_.message)
      .value
  case response                    =>
    response
      .as[String]
      .map(body => Left(s"Request failed with status '${response.status.code}' and body '$body'"))
}
result.unsafeRunSync()
