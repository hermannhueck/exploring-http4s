package userguide._13httpclient.contributors

import cats.effect.{IO, IOApp}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s._

object DottyContributors05BlazeClient extends IOApp.Simple {

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
    BlazeClientBuilder[IO]
      .resource
      .use { client: Client[IO] =>
        sendGetRequest(client, uri)
      }

  def sendGetRequest(client: Client[IO], uri: Uri): IO[Either[String, List[Contributor]]] = {

    import io.circe.generic.auto._ // auto-gernerate and implicitly provide Decoder for Contributor
    import org.http4s.circe.CirceEntityDecoder._

    // implicitly[Decoder[Contributor]]
    // implicitly[Decoder[List[Contributor]]]

    client.get[Either[String, List[Contributor]]](uri) {
      case Status.Successful(response) =>
        response
          .attemptAs[List[Contributor]]
          .leftMap(_.message)
          .value
      case response                    =>
        response
          .as[String]
          .map { body =>
            Left(s"Request failed. Status: ${response.status.code}, message $body")
          }
    }
  }
}
