package userguide._13httpclient.contributors

import cats.effect.{IO, IOApp}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client
import org.http4s._

object DottyContributors01 extends IOApp.Simple {

  val user = "lampepfl"
  val repo = "dotty"

  override val run: IO[Unit] = for {
    result <- queryContributors(user, repo)
    _      <- IO(printQueryResult(user, repo, result))
  } yield ()

  def queryContributors(user: String, repo: String): IO[Either[String, List[Contributor]]] = {

    val uriString = s"https://api.github.com/repos/$user/$repo/contributors"

    Uri.fromString(uriString) match {
      case Left(error) => IO.pure(Left(error.message))
      case Right(uri)  => getContributorsFromUri(uri)
    }
  }

  def getContributorsFromUri(uri: Uri): IO[Either[String, List[Contributor]]] =
    EmberClientBuilder
      .default[IO]
      .build
      .use { client: Client[IO] =>
        sendGetRequest(client, uri)
          .map { errOrbody =>
            errOrbody.flatMap(parseBody)
          }
      }

  def sendGetRequest(client: Client[IO], uri: Uri): IO[Either[String, String]] =
    client.get[Either[String, String]](uri) {
      case Status.Successful(response) =>
        response
          .attemptAs[String]
          .leftMap(_.message)
          .value
      case response                    =>
        response
          .as[String]
          .map { body =>
            Left(s"Request failed. Status: ${response.status.code}, message $body")
          }
    }

  import io.circe._
  import cats.implicits._ // for traverse and leftMap

  def parseBody(body: String): Either[String, List[Contributor]] = {
    for {
      json             <- io.circe.parser.parse(body)
      contributorsJson <- json.hcursor.as[List[Json]]
      contributors     <- contributorsJson.traverse(contributorJson2Contributor)
    } yield contributors
      .sortBy(_.contributions)
      .reverse
  }.leftMap(_.toString)
}
