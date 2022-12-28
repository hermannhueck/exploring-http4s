package userguide._13httpclient.contributors

import cats.effect.{IO, IOApp}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client
import org.http4s._
import org.http4s.circe._
import io.circe._

object DottyContributors02 extends IOApp.Simple {

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

  def sendGetRequest(client: Client[IO], uri: Uri): IO[Either[String, List[Json]]] = {

    implicit def decodeListJson[A: Decoder]: EntityDecoder[IO, List[A]] = jsonOf[IO, List[A]]
    // implicitly[Decoder[Json]]
    // implicitly[Decoder[List[Json]]]

    client.get[Either[String, List[Json]]](uri) {
      case Status.Successful(response) =>
        response
          .attemptAs[List[Json]]
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

  import io.circe._
  import cats.implicits._ // for traverse and leftMap

  def parseBody(contributorsJson: List[Json]): Either[String, List[Contributor]] =
    contributorsJson
      .traverse(contributorJson2Contributor)
      .map { contributors =>
        contributors.sortBy(_.contributions).reverse
      }
      .leftMap(_.toString)

  def contributorJson2Contributor(contributorJson: Json): Either[Error, Contributor] =
    for {
      login         <- contributorJson.hcursor.downField("login").as[String]
      contributions <- contributorJson.hcursor.downField("contributions").as[Int]
    } yield Contributor(login, contributions)
}
