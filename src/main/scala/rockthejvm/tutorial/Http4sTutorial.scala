// see:
// https://blog.rockthejvm.com/http4s-tutorial/
// https://www.youtube.com/watch?v=v_gv6LsWdT0&list=PLmtsMNDRU0ByzHzqLdoaeuKntdwCCB1d3&index=4

package rockthejvm.tutorial

import java.time.Year
import java.util.UUID

import scala.collection.mutable
import scala.util.Try

import cats._
import cats.effect._
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._
import org.typelevel.ci.CIString

object domain {

  type Actor = String

  case class Movie(id: String, title: String, year: Int, actors: List[Actor], director: String)

  case class Director(firstName: String, lastName: String) {
    override def toString: String = s"$firstName $lastName"
  }
}

object IMDB {

  import domain._

  private val directors: mutable.Map[Actor, Director] =
    mutable.Map("Zack Snyder" -> Director("Zack", "Snyder"))

  def addDirector(name: String, director: Director): Unit =
    directors += name -> director

  def findDirectorByName(name: String): Option[Director] =
    directors.get(name)

  private val snjl: Movie = Movie(
    "6bcbca1e-efd3-411d-9f7c-14b872444fce",
    "Zack Snyder's Justice League",
    2021,
    List("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa"),
    "Zack Snyder"
  )

  private val movies: Map[String, Movie] = Map(snjl.id -> snjl)

  def findMovieById(movieId: UUID): Option[Movie] =
    movies.get(movieId.toString)

  def findMoviesByDirector(director: String): List[Movie] =
    movies.values.filter(_.director == director).toList

}

object Http4sTutorial extends IOApp.Simple {

  import domain._

  object DirectorQueryParamMatcher extends QueryParamDecoderMatcher[String]("director")

  // implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
  //   QueryParamDecoder[Int].map(Year.of)
  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].emap { yearInt =>
      Try(Year.of(yearInt))
        .toEither
        .leftMap(t => ParseFailure(t.getMessage, t.toString))
    }
  // object YearQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Year]("year")
  object YearQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Year]("year")

  def movieRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      // GET all movies directed by a director and published in a year
      // route: GET /movies?director=Zack%20Snyder&year=2021
      case GET -> Root / "movies" :? DirectorQueryParamMatcher(director) +& YearQueryParamMatcher(maybeYear) =>
        maybeYear match {
          case Some(year) =>
            year.fold(
              _ => BadRequest("The given year is not valid"),
              year =>
                Ok(
                  IMDB
                    .findMoviesByDirector(director)
                    .filter(_.year == year.getValue)
                    .asJson
                )
            )
          case None       =>
            Ok(
              IMDB
                .findMoviesByDirector(director)
                .asJson
            )
        }
      // GET all actors of a movie
      // route: GET /movies/aa4f0f9c-c703-4f21-8c05-6a0c8f2052f0/actors
      case GET -> Root / "movies" / UUIDVar(movieId) / "actors"                                              =>
        IMDB.findMovieById(movieId).map(_.actors) match {
          case Some(actors) => Ok(actors.asJson)
          case _            => NotFound(s"No movie with id $movieId found")
        }
    }
  }

  object DirectorVar {

    def unapply(str: String): Option[Director] = {
      if (str.nonEmpty && str.matches(".* .*")) {
        Try {
          val tokens = str.split(' ')
          Director(tokens(0), tokens(1))
        }.toOption
      } else None
    }
  }

  def directorRoutes[F[_]: Concurrent]: HttpRoutes[F] = {
    val dsl                                                  = Http4sDsl[F]
    import dsl._
    implicit val directorDecoder: EntityDecoder[F, Director] = jsonOf[F, Director]
    HttpRoutes.of[F] {
      // GET details of a director
      // route: GET /directors/Zack%20Snyder
      case GET -> Root / "directors" / DirectorVar(director) =>
        IMDB.findDirectorByName(director.toString) match {
          case Some(dir) =>
            Ok(dir.asJson, Header.Raw(CIString("My-Custom-Header"), "value"))
          case None      =>
            NotFound(s"No director called $director found")
        }
      // POST a new director
      // Inserting a new director
      case req @ POST -> Root / "directors"                  =>
        for {
          director <- req.as[Director]
          _         = IMDB.addDirector(director.toString, director)
          res      <- Ok.headers(`Content-Encoding`(ContentCoding.gzip))
                        .map(_.addCookie(ResponseCookie("My-Cookie", "value")))
        } yield res
    }
  }

  def allRoutes[F[_]: Concurrent]: HttpRoutes[F] = {
    import cats.syntax.semigroupk._
    movieRoutes[F] <+> directorRoutes[F]
  }

  def allRoutesComplete[F[_]: Concurrent]: HttpApp[F] = {
    allRoutes.orNotFound
  }

  import org.http4s.server.blaze.BlazeServerBuilder
  // import scala.concurrent.ExecutionContext.global

  // val movieApp = allRoutesComplete[IO]
  // BlazeServerBuilder[IO](global)
  //   .bindHttp(8080, "localhost")
  //   .withHttpApp(movieApp)
  // // ...it's not finished yet

  val apis = Router(
    "/api"         -> movieRoutes[IO],
    "/api/private" -> directorRoutes[IO]
  ).orNotFound

  // BlazeServerBuilder[IO](global)
  //   .bindHttp(8080, "localhost")
  //   .withHttpApp(apis)

  @annotation.nowarn("cat=deprecation")
  override def run: IO[Unit] =
    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(8080, "localhost")
      .withHttpApp(apis)
      .resource
      .use(_ => IO.never)
  // .as(ExitCode.Success)

  // import hutil.stringformat._
  // val run: IO[Unit] =
  //   for {
  //     _ <- IO.println(dash80.green)
  //     _ <- IO.println("Hello Http4s")
  //     _ <- IO.println(dash80.green)
  //   } yield ()
}
