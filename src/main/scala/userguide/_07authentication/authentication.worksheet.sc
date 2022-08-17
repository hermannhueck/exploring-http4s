import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

// ----- Built in Authentication -----

/*
  ----- From package org.http4s:

  final case class ContextRequest[F[_], A](context: A, req: Request[F])

  type AuthedRequest[F[_], T] = ContextRequest[F, T]

  type AuthedRoutes[T, F[_]] = Kleisli[OptionT[F, *], AuthedRequest[F, T], Response[F]]

  type ContextRoutes[T, F[_]] = Kleisli[OptionT[F, *], ContextRequest[F, T], Response[F]]

  ----- From package org.http4s.server:

  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]

  type ContextMiddleware[F[_], T] =
    Middleware[OptionT[F, *], ContextRequest[F, T], Response[F], Request[F], Response[F]]

  type AuthMiddleware[F[_], T] =
    Middleware[OptionT[F, *], AuthedRequest[F, T], Response[F], Request[F], Response[F]]
 */

case class User(id: Long, name: String)

val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
  Kleisli(_ => OptionT.liftF(IO(User(1, "Bob"))))

val middleware: AuthMiddleware[IO, User] =
  AuthMiddleware(authUser)

val authedRoutes: AuthedRoutes[User, IO] =
  AuthedRoutes.of { case GET -> Root / "welcome" as user =>
    Ok(s"Welcome, ${user.name}")
  }

val service: HttpRoutes[IO] = middleware(authedRoutes)

// ----- Composing Authenticated Routes -----

val spanishRoutes: AuthedRoutes[User, IO] =
  AuthedRoutes.of { case GET -> Root / "hola" as user =>
    Ok(s"Hola, ${user.name}")
  }

val spanishRoutes2: AuthedRoutes[User, IO] =
  AuthedRoutes.of { case req: AuthedRequest[IO, User] =>
    Ok(s"Hola, ${req.context.name}")
  }

val frenchRoutes: HttpRoutes[IO] =
  HttpRoutes.of { case GET -> Root / "bonjour" =>
    Ok(s"Bonjour")
  }

val serviceSpanish: HttpRoutes[IO] = middleware(spanishRoutes) <+> frenchRoutes

import scala.util.chaining._
import userguide.util._

// shold return '401 Unauthorized' instead of '404 Not Found'
serviceSpanish.orNotFound(Request[IO](Method.GET, uri"/bad")).unsafeRunSync().status
serviceSpanish.orNotFound(Request[IO](Method.GET, uri"/")).unsafeRunSync().status
serviceSpanish.orNotFound(Request[IO](Method.GET, uri"/welcome")).unsafeRunSync().status
serviceSpanish.orNotFound(Request[IO](Method.GET, uri"/bonjour")).unsafeRunSync().status
serviceSpanish.orNotFound(Request[IO](Method.GET, uri"/hola")).unsafeRunSync().status
serviceSpanish
  .orNotFound(Request[IO](Method.GET, uri"/hola"))
  .unsafeRunSync()
  .pipe(evalBodyAsString)

val serviceRouter = {
  Router(
    "/spanish" -> middleware(spanishRoutes),
    "/french"  -> frenchRoutes
  )
}

serviceRouter.orNotFound(Request[IO](Method.GET, uri"/french/bonjour")).unsafeRunSync().status
serviceRouter.orNotFound(Request[IO](Method.GET, uri"/spanish/hola")).unsafeRunSync().status
serviceRouter
  .orNotFound(Request[IO](Method.GET, uri"/french/bonjour"))
  .unsafeRunSync()
  .pipe(evalBodyAsString)
serviceRouter
  .orNotFound(Request[IO](Method.GET, uri"/spanish/hola"))
  .unsafeRunSync()
  .pipe(evalBodyAsString)

val middlewareWithFallThrough: AuthMiddleware[IO, User] =
  AuthMiddleware.withFallThrough(authUser)

val serviceSF: HttpRoutes[IO] =
  middlewareWithFallThrough(spanishRoutes) <+> frenchRoutes

val serviceFS: HttpRoutes[IO] =
  frenchRoutes <+> middlewareWithFallThrough(spanishRoutes)

import org.http4s.client.dsl.io._

serviceSF.orNotFound(GET(uri"/bonjour")).unsafeRunSync().status
serviceSF.orNotFound(GET(uri"/hola")).unsafeRunSync().status

serviceFS.orNotFound(GET(uri"/bonjour")).unsafeRunSync().status
serviceFS.orNotFound(GET(uri"/hola")).unsafeRunSync().status

// ----- Returning an Error Response -----

val authUserEither: Kleisli[IO, Request[IO], Either[String, User]] = Kleisli(_ => IO(???))

val onFailure: AuthedRoutes[String, IO] =
  Kleisli(req => OptionT.liftF(Forbidden(req.context)))

val authMiddleware = AuthMiddleware(authUserEither, onFailure)

val serviceKleisli: HttpRoutes[IO] = authMiddleware(authedRoutes)

// ----- Implementing authUser -----

import org.reactormonk.{CryptoBits, PrivateKey}
import scala.io.Codec
import scala.util.Random

val key    = PrivateKey(Codec.toUTF8(Random.alphanumeric.take(20).mkString("")))
val crypto = CryptoBits(key)
val clock  = java.time.Clock.systemUTC

// gotta figure out how to do the form
def verifyLogin(request: Request[IO]): IO[Either[String, User]] =
  ???

val logIn: Kleisli[IO, Request[IO], Response[IO]] =
  Kleisli { request =>
    verifyLogin(request: Request[IO]).flatMap {
      case Left(error) =>
        Forbidden(error)
      case Right(user) => {
        val message = crypto.signToken(user.id.toString, clock.millis.toString)
        Ok("Logged in!").map(_.addCookie(ResponseCookie("authcookie", message)))
      }
    }
  }

import org.http4s.headers.Cookie

@annotation.nowarn("cat=unused")
def retrieveUser: Kleisli[IO, Long, User] = Kleisli(id => IO(???))

val authUserCookie: Kleisli[IO, Request[IO], Either[String, User]] =
  Kleisli { request =>
    val message: Either[String, Long] = for {
      header: Cookie        <- request
                                 .headers
                                 .get[Cookie]
                                 .toRight("Cookie parsing error")
      cookie: RequestCookie <- header
                                 .values
                                 .toList
                                 .find(_.name == "authcookie")
                                 .toRight("Couldn't find the authcookie")
      token: String         <- crypto
                                 .validateSignedToken(cookie.content)
                                 .toRight("Cookie invalid")
      message: Long         <- Either
                                 .catchOnly[NumberFormatException](token.toLong)
                                 .leftMap(_.toString)
    } yield message
    message.traverse(retrieveUser.run)
  }

// ----- Authorization Header -----

import org.http4s.headers.Authorization

val authUserHeaders: Kleisli[IO, Request[IO], Either[String, User]] =
  Kleisli { request =>
    val message = for {
      header: Authorization <- request
                                 .headers
                                 .get[Authorization]
                                 .toRight("Couldn't find an Authorization header")
      token: String         <- crypto
                                 .validateSignedToken(header.credentials.toString)
                                 .toRight("Invalid token")
      message: Long         <- Either
                                 .catchOnly[NumberFormatException](token.toLong)
                                 .leftMap(_.toString)
    } yield message
    message.traverse(retrieveUser.run)
  }
