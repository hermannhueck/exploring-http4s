package userguide._16jsonhandling

import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.ember.client._
import org.http4s.implicits._

import Domain._
import io.circe.Json

object HelloClientApp extends IOApp.Simple {

  implicit val userDecoder: EntityDecoder[IO, User] = jsonOf[IO, User]

  def helloClient(name: String): IO[Json] = {
    // Encode a User request
    val req = POST(User(name).asJson, uri"http://localhost:8080/hello")
    println(req.bodyText)
    // Create a client
    // Note: this client is used exactly once, and discarded
    // Ideally you should .build.use it once, and share it for multiple requests
    EmberClientBuilder.default[IO].build.use { httpClient =>
      // Decode a Hello response
      httpClient.expect(req)(jsonOf[IO, Json])
    }
  }

  val run =
    helloClient("Alice")
      .flatTap(IO.println)
      .void
}
