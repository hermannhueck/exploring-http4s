package userguide._15streaming

import org.http4s._
import org.http4s.ember.client._
import org.http4s.client.oauth1
import org.http4s.client.oauth1.ProtocolParameter._
import org.http4s.implicits._
import cats.effect._
import fs2.Stream
import fs2.io.stdout
// import fs2.text.{lines, utf8Encode}
import io.circe.Json
import org.typelevel.jawn.fs2._

class TWStream[F[_]: Async] {
  // jawn-fs2 needs to know what JSON AST you want
  implicit val f = new io.circe.jawn.CirceSupportParser(None, false).facade

  /* These values are created by a Twitter developer web app.
   * OAuth signing is an effect due to generating a nonce for each `Request`.
   */
  def sign(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(
      req: Request[F]
  ): F[Request[F]] = {
    val consumer = Consumer(consumerKey, consumerSecret)
    val token    = Token(accessToken, accessSecret)
    oauth1.signRequest(
      req,
      consumer,
      Some(token),
      realm = None,
      timestampGenerator = Timestamp.now,
      nonceGenerator = Nonce.now
    )
  }

  /* Create a http client, sign the incoming `Request[F]`, stream the `Response[IO]`, and
   * `parseJsonStream` the `Response[F]`.
   * `sign` returns a `F`, so we need to `Stream.eval` it to use a for-comprehension.
   */
  def jsonStream(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)(
      req: Request[F]
  ): Stream[F, Json] =
    for {
      client        <- Stream.resource(EmberClientBuilder.default[F].build)
      signedRequest <- Stream.eval(sign(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      response      <- client.stream(signedRequest)
      jsonResult    <- response.body.chunks.parseJsonStream
    } yield jsonResult

  /* Stream the sample statuses.
   * Plug in your four Twitter API values here.
   * We map over the Circe `Json` objects to pretty-print them with `spaces2`.
   * Then we `to` them to fs2's `lines` and then to `stdout` `Sink` to print them.
   */
  val stream: Stream[F, Unit] = {
    val req = Request[F](Method.GET, uri"https://stream.twitter.com/1.1/statuses/sample.json")
    val s   = jsonStream("<consumerKey>", "<consumerSecret>", "<accessToken>", "<accessSecret>")(req)
    s.map(_.spaces2).through(fs2.text.lines).through(fs2.text.utf8.encode).through(stdout)
  }

  /** Compile our stream down to an effect to make it runnable */
  def run: F[Unit] =
    stream.compile.drain
}

object TWStreamApp extends IOApp.Simple {
  val run =
    new TWStream[IO].run.void
}
