import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.dsl.io._
import org.http4s.headers._

val request: Request[IO] = GET(
  uri"https://my-lovely-api.com/",
  Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
  Accept(MediaType.application.json)
)
