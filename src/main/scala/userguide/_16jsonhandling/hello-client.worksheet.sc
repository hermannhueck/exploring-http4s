import cats.effect._

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.ember.client._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

import userguide._16jsonhandling.Domain._

implicit val userDecoder: EntityDecoder[IO, User] = jsonOf[IO, User]

def helloClientToJson(name: String): IO[Json] = {
  // Encode a User request
  val req = POST(User(name).asJson, uri"http://localhost:8080/hello")
  // Create a client
  // Note: this client is used exactly once, and discarded
  // Ideally you should .build.use it once, and share it for multiple requests
  EmberClientBuilder.default[IO].build.use { httpClient =>
    // Decode a Hello response to Json
    httpClient.expect(req)(jsonOf[IO, Json])
  }
}

def helloClientToHello(name: String): IO[Hello] = {
  // Encode a User request
  val req = POST(User(name).asJson, uri"http://localhost:8080/hello")
  println(req)
  // Create a client
  // Note: this client is used exactly once, and discarded
  // Ideally you should .build.use it once, and share it for multiple requests
  EmberClientBuilder.default[IO].build.use { httpClient =>
    // Decode a Hello response
    implicit val helloDecoder: EntityDecoder[IO, Hello] = jsonOf[IO, Hello]
    httpClient.expect(req)(helloDecoder) // (jsonOf[IO, Hello])
  }
}

helloClientToJson("Alice")
  .unsafeRunSync()

helloClientToHello("Alice")
  .unsafeRunSync()
