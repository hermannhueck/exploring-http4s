/*
  See:
  - https://www.youtube.com/watch?v=DxZIuvSDvyA
  - https://blog.rockthejvm.com/scala-http4s-authentication/
 */

package rockthejvm.http4sauth

import cats.effect._
import org.http4s._
import org.http4s.server._
import org.http4s.ember.server._
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Cookie
import cats._
// import cats.data._
import cats.syntax.all._
import org.http4s.server.middleware.authentication.DigestAuth
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.time.LocalTime

object Http4sDemo4AuthSessions extends IOApp.Simple {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def server[F[_]: Async: Network](httpApp: HttpApp[F]): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(???)
      .build

  override val run: IO[Unit] =
    server[IO](???).useForever

  // 4. ========== Authentication using a Session ==========

  // 1. User logs in with username and password
  // 2. Server replies with a Set-Cookie header
  // 3. User sends the cookie back to the server with every subsequent request
  // 4. Server validates the cookie and grants access if the cookie is valid

  def setToken(user: String, date: String): String =
    Base64
      .getEncoder
      .encodeToString(s"$user:$date".getBytes(StandardCharsets.UTF_8))

  def getUser(token: String): String =
    Base64 // ignore errors for simplicity
      .getDecoder
      .decode(token.getBytes)
      .toString
      .split(":")
      .toList
      .head

  // login endpoint
  def authedRoutes[F[_]: Monad]: AuthedRoutes[User, F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of { case GET -> Root / "welcome" as user =>
      val cookie =
        ResponseCookie(
          "sessioncookie",
          setToken(user.name, LocalTime.now().toString),
          maxAge = Some(24 * 3600L)
        )
      Ok(s"Welcome, $user!")
        .map(_.addCookie(cookie))
    }
  }

  def userDigestAuthMiddlewareResource[F[_]: Async]: Resource[F, AuthMiddleware[F, User]] =
    Resource.eval(userDigestAuthMiddleware[F])

  val realm = "http://localhost:8080"

  def userDigestAuthMiddleware[F[_]: Async]: F[AuthMiddleware[F, User]] =
    // apply is side-effecting, hence we use applyF
    DigestAuth.applyF[F, User](realm = realm, store = digestAuthStore)

  def digestAuthStore[F[_]: Sync] = DigestAuth.Md5HashedAuthStore(searchFunction[F])

  def searchFunction[F[_]: Sync]: String => F[Option[(User, String)]] = {
    // search user in the database
    // if found, return Some(user, password)
    // if not found, return None
    // case "alice" => (User(123L, "alice") -> "alicepw").some.pure[F]
    case "alice" =>
      for {
        user <- User(123L, "alice").pure[F] // search user in the database
        hash <- DigestAuth.Md5HashedAuthStore.precomputeHash(user.name, realm, "alicepw")
      } yield (user, hash).some
    case _       => none.pure[F]
  }

  // endpoints that require the session cookie
  def cookieAccessRoutes[F[_]: Async]: HttpRoutes[F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "statement" / user =>
        OK("Here is your financial statement.")
      case GET -> Root / "logout"           =>
        OK("Logging out...")
          .map(_.removeCookie("sessioncookie"))
    }
  }

  // prove that the client has sent a valid cookie
  def cookieCheckerApp[F[_]: Monad](app: HttpApp[F]): HttpApp[F] = HttpApp[F] { req =>
    val maybeCookie = req.cookies.get[Cookie]
    ??? // TODO
  }
}
