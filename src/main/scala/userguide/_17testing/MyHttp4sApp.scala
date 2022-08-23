package userguide._17testing

import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.effect._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

case class User(name: String, age: Int)

trait UserRepo[F[_]] {
  def find(userId: String): F[Option[User]]
}

object MyHttp4sApp {

  def httpRoutes[F[_]: Async](
      repo: UserRepo[F]
  ): HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / "user" / id =>
    repo.find(id).map {
      case None       =>
        Response(status = Status.NotFound)
      case Some(user) =>
        implicit val UserEncoder: Encoder[User] = deriveEncoder[User]
        Response(status = Status.Ok).withEntity(user.asJson)
    }
  }
}
