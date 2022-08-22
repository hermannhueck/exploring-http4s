import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.dsl.io._
import cats._, cats.effect._, cats.implicits._, cats.data._

sealed trait Resp
case class Audio(body: String) extends Resp
case class Video(body: String) extends Resp

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

val response = Ok("").map(_.withContentType(`Content-Type`(MediaType.audio.ogg)))

val audioDec = EntityDecoder.decodeBy(MediaType.audio.ogg) { (m: Media[IO]) =>
  EitherT {
    m.as[String].map(s => Audio(s).asRight[DecodeFailure])
  }
}

val videoDec = EntityDecoder.decodeBy(MediaType.video.ogg) { (m: Media[IO]) =>
  EitherT {
    m.as[String].map(s => Video(s).asRight[DecodeFailure])
  }
}

implicit val bothDec = audioDec.widen[Resp] orElse videoDec.widen[Resp]

response.flatMap(_.as[Resp]).unsafeRunSync()
