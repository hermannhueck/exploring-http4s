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
import cats._
import cats.syntax.all._
import org.http4s.server.middleware.authentication.DigestAuth

object Http4sDemo3AuthDigest extends IOApp.Simple {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def server[F[_]: Async: Network](httpApp: HttpApp[F]): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build

  override val run: IO[Unit] =
    (for {
      middleware <- userDigestAuthMiddlewareResource[IO]
      httpApp     = middleware.apply(authedRoutes).orNotFound
      _          <- server(httpApp)
    } yield ()).useForever

  // 3. ========== Digest Authentication ==========

  def authedRoutes[F[_]: Monad]: AuthedRoutes[User, F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of { case GET -> Root / "welcome" as user =>
      Ok(s"Welcome, ${user.name}!")
    }
  }

  def userDigestAuthMiddlewareResource[F[_]: Async]: Resource[F, AuthMiddleware[F, User]] =
    Resource.eval(userDigestAuthMiddleware[F])

  val realm = "http://localhost:8080"

  def userDigestAuthMiddleware[F[_]: Async]: F[AuthMiddleware[F, User]] =
    // apply is side-effecting, hence we use applyF
    DigestAuth.applyF[F, User](realm = realm, store = digestAuthStore)

  // def userDigestAuthMiddleware[F[_]: Sync]: AuthMiddleware[F, User] =
  //   // apply is side-effecting, hence we use applyF
  //   DigestAuth.apply[F, User](realm = realm, store = searchFunction)

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
}