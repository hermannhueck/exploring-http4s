package userguide._16jsonhandling

import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._
// import io.circe.Json

// import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.ember.client._
import org.http4s.implicits._

import Domain._

object HelloClientApp extends IOApp.Simple {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def helloClient(name: String): IO[Hello] = {
    // Encode a User request
    val req = POST(User(name).asJson, uri"http://localhost:8080/hello")
    println(req.bodyText)
    // Create a client
    // Note: this client is used exactly once, and discarded
    // Ideally you should .build.use it once, and share it for multiple requests
    EmberClientBuilder.default[IO].build.use { httpClient =>
      // Decode a Hello response
      // implicit val decoder: EntityDecoder[IO, Hello] =
      //   jsonOf[IO, Hello]
      httpClient.expect(req)(jsonOf[IO, Hello])
    }
  }

  val run: IO[Unit] =
    helloClient("Alice")
      .flatTap(IO.println)
      .void
}
