import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

case class User(name: String, age: Int)
implicit val UserEncoder: Encoder[User] = deriveEncoder[User]

trait UserRepo[F[_]] {
  def find(userId: String): F[Option[User]]
}

def httpRoutes[F[_]: Async](repo: UserRepo[F]): HttpRoutes[F] =
  HttpRoutes.of[F] { case GET -> Root / "user" / id =>
    repo.find(id).map {
      case None       => Response(status = Status.NotFound)
      case Some(user) => Response(status = Status.Ok).withEntity(user.asJson)
    }
  }

// Return true if match succeeds; otherwise false
def check[A](
    actual: IO[Response[IO]],
    expectedStatus: Status,
    expectedBody: Option[A]
)(implicit
    ev: EntityDecoder[IO, A]
): Boolean = {
  val actualResp  = actual.unsafeRunSync()
  val statusCheck = actualResp.status == expectedStatus
  val bodyCheck   = expectedBody.fold[Boolean](
    // Verify Response's body is empty.
    actualResp.body.compile.toVector.unsafeRunSync().isEmpty
  )(
    // Verify Response's body is expected A (Json in success example).
    expected => actualResp.as[A].unsafeRunSync() == expected
  )
  statusCheck && bodyCheck
}

// Let's define service by passing a UserRepo that returns Ok(user).

val success: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] = IO.pure(Some(User("johndoe", 42)))
}

val response: IO[Response[IO]] = httpRoutes[IO](success)
  .orNotFound
  .run(
    Request(method = Method.GET, uri = uri"/user/not-used")
  )

val expectedJson = Json.obj(
  "name" := "johndoe",
  "age"  := 42
)

check[Json](response, Status.Ok, Some(expectedJson))

// Next, let's define a service with a userRepo that returns None to any input.

val foundNone: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] = IO.pure(None)
}

val respFoundNone: IO[Response[IO]] = httpRoutes[IO](foundNone)
  .orNotFound
  .run(
    Request(method = Method.GET, uri = uri"/user/not-used")
  )

check[Json](respFoundNone, Status.NotFound, None)

// Finally, let's pass a Request which our service does not handle.

val doesNotMatter: UserRepo[IO] = new UserRepo[IO] {
  def find(id: String): IO[Option[User]] =
    IO.raiseError(new RuntimeException("Should not get called!"))
}

val respNotFound: IO[Response[IO]] = httpRoutes[IO](doesNotMatter)
  .orNotFound
  .run(
    Request(method = Method.GET, uri = uri"/not-a-matching-path")
  )

check[String](respNotFound, Status.NotFound, Some("Not found"))

// ----- Using Client -----

val httpApp: HttpApp[IO] = httpRoutes[IO](success).orNotFound

import org.http4s.client.Client

val request: Request[IO] = Request(method = Method.GET, uri = uri"/user/not-used")

val client: Client[IO] = Client.fromHttpApp(httpApp)

val resp: IO[Json] = client.expect[Json](request)

resp.unsafeRunSync()
expectedJson

resp.unsafeRunSync() == expectedJson
assert(resp.unsafeRunSync() == expectedJson)

import munit.Assertions._

assertEquals(resp.unsafeRunSync(), expectedJson)

val (status, bodyText) =
  Client
    .fromHttpApp(httpApp)
    .run(Request(method = Method.GET, uri = uri"/not-a-matching-path"))
    .use { response =>
      IO(response.status -> response.as[String].unsafeRunSync())
    }
    .unsafeRunSync()

assertEquals(status, Status.NotFound)
assertEquals(bodyText, "Not found")
