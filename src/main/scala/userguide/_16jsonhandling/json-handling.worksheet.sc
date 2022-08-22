// import cats._
// import cats.implicits._
import cats.effect._
import io.circe._
import io.circe.literal._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Sending Raw JSON -----

def hello(name: String): Json =
  json"""{"hello": $name}"""

val greeting: Json = hello("world")

// Ok(greeting).unsafeRunSync()
// error: Cannot convert from io.circe.Json to an Entity, because no EntityEncoder[cats.effect.IO, io.circe.Json] instance could be found.
// Ok(greeting).unsafeRunSync()
// ^^^^^^^^^^^^

import org.http4s.circe._

Ok(greeting).unsafeRunSync()

import org.http4s.client.dsl.io._

POST(json"""{"name": "Alice"}""", uri"/hello")

// ----- Encoding case classes as JSON -----

case class Hello(name: String)
case class User(name: String)

import io.circe.syntax._

implicit val HelloEncoder: Encoder[Hello] =
  Encoder.instance { (hello: Hello) =>
    json"""{"hello": ${hello.name}}"""
  }

Hello("Alice").asJson

import io.circe.generic.auto._

User("Alice").asJson

Ok(Hello("Alice").asJson).unsafeRunSync()

POST(User("Bob").asJson, uri"/hello")

// ----- Receiving Raw JSON -----

Ok("""{"name":"Alice"}""").flatMap(_.as[Json]).unsafeRunSync()

POST("""{"name":"Bob"}""", uri"/hello").as[Json].unsafeRunSync()

// ----- Decoding JSON to a case class -----

implicit val userDecoder = jsonOf[IO, User]

Ok("""{"name":"Alice"}""").flatMap(_.as[User]).unsafeRunSync()

POST("""{"name":"Bob"}""", uri"/hello").as[User].unsafeRunSync()
