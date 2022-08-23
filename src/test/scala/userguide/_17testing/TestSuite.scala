package userguide._17testing

import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._

class TestSuite extends munit.FunSuite {

  import cats.effect.unsafe.IORuntime
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  import MyHttp4sApp._

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

  test("Test service that finds a User and returns Ok(user)") {

    val success: UserRepo[IO] = new UserRepo[IO] {
      def find(id: String): IO[Option[User]] = IO.pure(Some(User("johndoe", 42)))
    }

    val response: IO[Response[IO]] =
      httpRoutes[IO](success)
        .orNotFound
        .run(Request(method = Method.GET, uri = uri"/user/not-used"))

    val expectedJson = Json.obj(
      "name" := "johndoe",
      "age"  := 42
    )

    assertEquals(check[Json](response, Status.Ok, Some(expectedJson)), true)
  }

  test("Test service that doesn't find a User and returns NotFound") {

    val foundNone: UserRepo[IO] = new UserRepo[IO] {
      def find(id: String): IO[Option[User]] = IO.pure(None)
    }

    val response: IO[Response[IO]] =
      httpRoutes[IO](foundNone)
        .orNotFound
        .run(Request(method = Method.GET, uri = uri"/user/not-used"))

    assertEquals(check[Json](response, Status.NotFound, None), true)
  }

  test("Test a Request which our service does not handle, i.e. returns NotFound") {

    val doesNotMatter: UserRepo[IO] = new UserRepo[IO] {
      def find(id: String): IO[Option[User]] =
        IO.raiseError(new RuntimeException("Should not get called!"))
    }

    val response: IO[Response[IO]] =
      httpRoutes[IO](doesNotMatter)
        .orNotFound
        .run(Request(method = Method.GET, uri = uri"/not-a-matching-path"))

    assertEquals(check[String](response, Status.NotFound, Some("Not found")), true)
  }

  import org.http4s.client.Client

  test("Test service that finds a User and returns Ok(user) - using a Client") {

    val success: UserRepo[IO] =
      _ => IO.pure[Option[User]](Some(User("johndoe", 42)))

    val httpApp: HttpApp[IO] =
      httpRoutes[IO](success).orNotFound

    val obtainedJson =
      Client
        .fromHttpApp(httpApp)
        .expect[Json](Request(method = Method.GET, uri = uri"/user/not-used"))
        .unsafeRunSync()

    val expectedJson = Json.obj(
      "name" := "johndoe",
      "age"  := 42
    )

    assertEquals(obtainedJson, expectedJson)
  }

  test("Test service that doesn't find a User and returns NotFound - using a Client") {

    val foundNone: UserRepo[IO] =
      _ => IO.pure[Option[User]](None)

    val httpApp: HttpApp[IO] =
      httpRoutes[IO](foundNone).orNotFound

    val obtainedStatus =
      Client
        .fromHttpApp(httpApp)
        .status(Request(method = Method.GET, uri = uri"/user/not-used"))
        .unsafeRunSync()

    assertEquals(obtainedStatus, Status.NotFound)
  }

  test("Test a Request which our service does not handle, i.e. returns NotFound - using a Client") {

    val doesNotMatter: UserRepo[IO] =
      _ => IO.raiseError(new RuntimeException("Should not get called!"))

    val httpApp: HttpApp[IO] =
      httpRoutes[IO](doesNotMatter).orNotFound

    // check only the status (using client.status)
    val obtainedStatus =
      Client
        .fromHttpApp(httpApp)
        .status(Request(method = Method.GET, uri = uri"/not-a-matching-path"))
        .unsafeRunSync()

    assertEquals(obtainedStatus, Status.NotFound)

    // check only the status and body (using client.run which returns a Resource[IO, Response[IO]])
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
  }
}
