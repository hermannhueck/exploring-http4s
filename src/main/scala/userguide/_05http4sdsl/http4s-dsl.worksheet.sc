import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- The Simplest Service -----

val service = HttpRoutes.of[IO] { case _ =>
  IO(Response(Status.Ok))
}

// ----- Testing the Service -----

val getRoot: Request[IO] = Request[IO](method = Method.GET, uri = uri"/")

val serviceIO: IO[Response[IO]] = service.orNotFound.run(getRoot)

val response: Response[IO] = serviceIO.unsafeRunSync()

// ----- Generating Responses -----

// Status Codes

val okIo: IO[Response[IO]] = Ok()
val ok: Response[IO]       = okIo.unsafeRunSync()

HttpRoutes
  .of[IO] { case _ =>
    Ok()
  }
  .orNotFound
  .run(getRoot)
  .unsafeRunSync()

HttpRoutes
  .of[IO] { case _ =>
    NoContent()
  }
  .orNotFound
  .run(getRoot)
  .unsafeRunSync()

// Headers

Ok("Ok response.").unsafeRunSync()
Ok("Ok response.").unsafeRunSync().headers

import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective.`no-cache`
import cats.data.NonEmptyList

Ok("Ok response.", `Cache-Control`(NonEmptyList(`no-cache`(), Nil)))
  .unsafeRunSync()
  .headers

Ok("Ok response.", "X-Auth-Token" -> "value")
  .unsafeRunSync()
  .headers

// Cookies

Ok("Ok response.")
  .map(_.addCookie(ResponseCookie("foo", "bar")))
  .unsafeRunSync()
  .headers

val cookieResp = {
  for {
    resp: Response[IO] <- Ok("Ok response.")
    now: HttpDate      <- HttpDate.current[IO]
  } yield resp.addCookie(ResponseCookie("foo", "bar", expires = Some(now), httpOnly = true, secure = true))
}

cookieResp.unsafeRunSync().headers

Ok("Ok response.").map(_.removeCookie("foo")).unsafeRunSync().headers

// Responding with a Body

Ok("Received request.").unsafeRunSync()
Ok("Received request.").unsafeRunSync().entity

import java.nio.charset.StandardCharsets.UTF_8
Ok("binary".getBytes(UTF_8)).unsafeRunSync()

// NoContent("does not compile")
// error: no arguments allowed for nullary method apply: ()(implicit F: cats.Applicative[cats.effect.IO]): cats.effect.IO[org.http4s.Response[cats.effect.IO]] in trait EmptyResponseGenerator

// Asynchronous Responses

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val ioFuture = Ok(IO.fromFuture(IO(Future {
  println("I run when the future is constructed.")
  "Greetings from the future!"
})))

ioFuture.unsafeRunSync()

val io = Ok(IO {
  println("I run when the IO is run.")
  "Mission accomplished!"
})

io.unsafeRunSync()

// Streaming Bodies

import fs2.Stream
import scala.concurrent.duration._

val drip: Stream[IO, String] =
  Stream.awakeEvery[IO](100.millis).map(_.toString).take(10)

val dripOutIO = drip
  .through(fs2.text.lines)
  .evalMap(s => { IO { println(s); s } })
  .compile
  .drain
dripOutIO.unsafeRunSync()

Ok(drip)
val body =
  Ok(drip)
    .unsafeRunSync()
    .entity
    .body
body
  .compile
  .toVector
  .unsafeRunSync()
body
  .through(fs2.text.utf8.decode)
  .compile
  .toVector
  .unsafeRunSync()
body
  .through(fs2.text.utf8.decode)
  .evalTap(IO.println)
  .compile
  .drain
  .unsafeRunSync()
// 100889861 nanoseconds
// 205052616 nanoseconds
// 305099628 nanoseconds
// 401219762 nanoseconds
// 505406633 nanoseconds
// 601761905 nanoseconds
// 704723778 nanoseconds
// 803784249 nanoseconds
// 904075583 nanoseconds
// 1001366169 nanoseconds

// ----- Matching and Extracting Requests -----

HttpRoutes.of[IO] { case GET -> Root / "hello" =>
  Ok("hello")
}

HttpRoutes.of[IO] { case GET -> Root =>
  Ok("root")
}

HttpRoutes.of[IO] { case GET -> Root / "hello" / name =>
  Ok(s"Hello, ${name}!")
}

HttpRoutes.of[IO] { case GET -> "hello" /: rest =>
  Ok(s"""Hello, ${rest.segments.mkString(" and ")}!""")
}

HttpRoutes
  .of[IO] { case GET -> "hello" /: rest =>
    Ok(s"""Hello, ${rest.segments.mkString(" and ")}!""")
  }                                                                   // : HttpRoutes[IO] ( = Http[OptionT[IO, *], IO] )
  .orNotFound                                                         // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
  .run(Request[IO](method = Method.GET, uri = uri"/hello/Alice/Bob")) // : IO[Response[IO]]
  .unsafeRunSync()                                                    // : Response[IO]
  .body                                                               // : EntityBody[IO] ( = Stream[IO, Byte] )
  .through(fs2.text.utf8.decode)                                      // : Stream[IO, String]
  .compile                                                            // : Stream.CompileOps[IO, IO, String]
  .toVector                                                           // : IO[Vector[String]]
  .unsafeRunSync()                                                    // : Vector[String]
  .head                                                               // : String
// "Hello, Alice and Bob!"

HttpRoutes.of[IO] { case GET -> Root / file ~ "json" =>
  Ok(s"""{"response": "You asked for $file"}""")
}

import scala.util.chaining._

val suffix = "json"
HttpRoutes
  .of[IO] { case GET -> Root / file ~ suffix =>
    Ok(s"""{"response": "You asked for file: $file.$suffix"}""")
  }
  .orNotFound // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
  .run(Request[IO](method = Method.GET, uri = uri"/user.json")) // : IO[Response[IO]]
  .unsafeRunSync()                                              // : Response[IO]
  .body                                                         // : EntityBody[IO] ( = Stream[IO, Byte] )
  .through(fs2.text.utf8.decode)                                // : Stream[IO, String]
  .compile                                                      // : : Stream.CompileOps[IO, IO, String]
  .toVector                                                     // : IO[Vector[String]]
  .unsafeRunSync()                                              // : Vector[String]
  .head                                                         // : String
  .tap(println)
// "{\"response\": \"You asked for file: user.json\"}"

// Handling Path Parameters

def getUserName(userId: Int): IO[String] = ???

val usersService = HttpRoutes.of[IO] { case GET -> Root / "users" / IntVar(userId) =>
  Ok(getUserName(userId))
}

import java.time.LocalDate
import scala.util.Try
import org.http4s.client.dsl.io._

object LocalDateVar {
  def unapply(str: String): Option[LocalDate] = {
    if (str.trim.isEmpty)
      Option.empty[LocalDate]
    else
      Try(LocalDate.parse(str)).toOption
  }
}

@annotation.nowarn("cat=unused")
def getTemperatureForecast(date: LocalDate): IO[Double] = IO(31.5)

val dailyWeatherService = HttpRoutes.of[IO] { case GET -> Root / "weather" / "temperature" / LocalDateVar(localDate) =>
  Ok(
    getTemperatureForecast(localDate)
      .map(temp => s"The temperature on $localDate will be: $temp")
  )
}

val req = GET(uri"/weather/temperature/2022-08-05")

dailyWeatherService              // : HttpRoutes[IO] ( = Http[OptionT[IO, *], IO] )
  .orNotFound(req)               // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
  .unsafeRunSync()               // : Response[IO]
  .body                          // : EntityBody[IO] ( = Stream[IO, Byte] )
  .through(fs2.text.utf8.decode) // : Stream[IO, String]
  .compile                       // : Stream.CompileOps[IO, IO, String]
  .toVector                      // : IO[Vector[String]]
  .unsafeRunSync()               // : Vector[String]
  .head                          // : String

import userguide.util._

dailyWeatherService       // : HttpRoutes[IO] ( = Http[OptionT[IO, *], IO] )
  .orNotFound(req)        // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
  .unsafeRunSync()        // : Response[IO]
  .pipe(evalBodyAsString) // : String

// Handling Matrix Path Parameters

import org.http4s.dsl.impl.MatrixVar

object FullNameExtractor extends MatrixVar("name", List("first", "last"))

val greetingService = HttpRoutes.of[IO] { case GET -> Root / "hello" / FullNameExtractor(first, last) / "greeting" =>
  Ok(s"Hello, $first $last.")
}

greetingService           // : HttpRoutes[IO] ( = Http[OptionT[IO, *], IO] )
  .orNotFound(
    GET(uri"/hello/name;first=john;last=doe/greeting")
  )                       // : HttpApp[IO] ( = Kleisli[IO, Request[IO], Response[IO]] )
  .unsafeRunSync()        // : Response[IO]
  .pipe(evalBodyAsString) // : String

greetingService
  .pipe(runWithRequest(_, GET(uri"/hello/name;first=john;last=doe/greeting")))
  .pipe(evalBodyAsString)

object FullNameAndIDExtractor extends MatrixVar("name", List("first", "last", "id"))

val greetingWithIdService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / FullNameAndIDExtractor(first, last, IntVar(id)) / "greeting" =>
    Ok(s"Hello, $first $last. Your User ID is $id.")
}

greetingWithIdService
  .pipe(runWithRequest(_, GET(uri"/hello/name;first=john;last=doe;id=123/greeting")))
  .pipe(evalBodyAsString)

// Handling Query Parameters

import java.time.Year

object CountryQueryParamMatcher extends QueryParamDecoderMatcher[String]("country")

implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
  QueryParamDecoder[Int].map(Year.of)
// yearQueryParamDecoder: QueryParamDecoder[Year] = org.http4s.QueryParamDecoder$$anon$7@4550a33d

object YearQueryParamMatcher extends QueryParamDecoderMatcher[Year]("year")

@annotation.nowarn("cat=unused")
def getAverageTemperatureForCountryAndYear(country: String, year: Year): IO[Double] =
  IO(9.1)

val averageTemperatureService = HttpRoutes.of[IO] {
  case GET -> Root / "weather" / "temperature" :? CountryQueryParamMatcher(country) +& YearQueryParamMatcher(year) =>
    Ok(
      getAverageTemperatureForCountryAndYear(country, year)
        .map(s"Average temperature for $country in $year was: " + _)
    )
}

averageTemperatureService
  .pipe(runWithRequest(_, GET(uri"/weather/temperature?country=Germany&year=2021")))
  .pipe(evalBodyAsString)

// Codec for Instant

import java.time.Instant
import java.time.format.DateTimeFormatter

implicit val isoInstantCodec: QueryParamCodec[Instant] =
  QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

object IsoInstantParamMatcher extends QueryParamDecoderMatcher[Instant]("timestamp")

// Optional Query Parameters

// already defined above
// implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
//   QueryParamDecoder[Int].map(Year.of)

object OptionalYearQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Year]("year")

def getAverageTemperatureForCurrentYear: IO[String]   = IO("10.1")
@annotation.nowarn("cat=unused")
def getAverageTemperatureForYear(y: Year): IO[String] = IO("9.1")

val routes = HttpRoutes
  .of[IO] { case GET -> Root / "temperature" :? OptionalYearQueryParamMatcher(maybeYear) =>
    maybeYear match {
      case None       =>
        Ok(getAverageTemperatureForCurrentYear)
      case Some(year) =>
        Ok(getAverageTemperatureForYear(year))
    }
  }

routes
  .pipe(runWithRequest(_, GET(uri"/temperature")))
  .pipe(evalBodyAsString)
// 10.1
routes
  .pipe(runWithRequest(_, GET(uri"/temperature?year=2021")))
  .pipe(evalBodyAsString)
// 9.1

// Invalid Query Parameter Handling

/* implicit */
val yearQueryParamDecoder2: QueryParamDecoder[Year] =
  QueryParamDecoder[Int]
    .emap(i =>
      Try(Year.of(i))
        .toEither
        .leftMap(t => ParseFailure(t.getMessage, t.toString))
    )

object YearQueryParamMatcher2 extends ValidatingQueryParamDecoderMatcher[Year]("year")(yearQueryParamDecoder2)

val routes2 = HttpRoutes
  .of[IO] { case GET -> Root / "temperature" :? YearQueryParamMatcher2(yearValidated) =>
    yearValidated.fold(
      parseFailures => BadRequest("unable to parse argument year; failures: " + parseFailures),
      year => Ok(getAverageTemperatureForYear(year))
    )
  }

routes2
  .pipe(runWithRequest(_, GET(uri"/temperature")))
  .pipe(evalBodyAsString)
// Not Found
routes2
  .pipe(runWithRequest(_, GET(uri"/temperature?year=2021")))
  .pipe(evalBodyAsString)
// 9.1
routes2
  .pipe(runWithRequest(_, GET(uri"/temperature?year=not-a-valid-year")))
  .pipe(evalBodyAsString)
// "unable to parse argument year; failures: NonEmptyList(org.http4s.ParseFailure: Query decoding Int failed: For input string: \"not-a-valid-year\")"

// Optional Invalid Query Parameter Handling

object LongParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Long]("long")

val routes3 = HttpRoutes.of[IO] { case GET -> Root / "number" :? LongParamMatcher(maybeNumber) =>
  val _: Option[cats.data.ValidatedNel[org.http4s.ParseFailure, Long]] = maybeNumber

  maybeNumber match {
    case None       =>
      BadRequest("missing number")
    case Some(long) =>
      long // : cats.data.ValidatedNel[org.http4s.ParseFailure, Long]
        .fold(
          parseFailures => BadRequest("unable to parse argument 'long'; failures: " + parseFailures),
          year => Ok(year.toString)
        ) // : IO[Response[IO]]
  }
}

routes3
  .pipe(runWithRequest(_, GET(uri"/number")))
  .pipe(evalBodyAsString)
// "missing number"
routes3
  .pipe(runWithRequest(_, GET(uri"/number?long=42")))
  .pipe(evalBodyAsString)
// "42"
routes3
  .pipe(runWithRequest(_, GET(uri"/number?long=not-a-valid-long")))
  .pipe(evalBodyAsString)
// "unable to parse argument 'long'; failures: NonEmptyList(org.http4s.ParseFailure: Query decoding Long failed: For input string: \"not-a-valid-long\")"
