import cats.effect._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._

case class TweetWithId(id: Int, message: String)
case class Tweet(message: String)

def getTweet(tweetId: Int): IO[Option[TweetWithId]]             = ???
def addTweet(tweet: Tweet): IO[TweetWithId]                     = ???
def updateTweet(id: Int, tweet: Tweet): IO[Option[TweetWithId]] = ???
def deleteTweet(id: Int): IO[Unit]                              = ???

implicit val tweetWithIdEncoder = jsonEncoderOf[TweetWithId]
implicit val tweetDecoder       = jsonOf[IO, Tweet]

val tweetService = HttpRoutes.of[IO] {
  case GET -> Root / "tweets" / IntVar(tweetId)       =>
    getTweet(tweetId)
      .flatMap(_.fold(NotFound())(Ok(_)))
  case req @ POST -> Root / "tweets"                  =>
    req
      .as[Tweet]
      .flatMap(addTweet)
      .flatMap(Ok(_))
  case req @ PUT -> Root / "tweets" / IntVar(tweetId) =>
    req
      .as[Tweet]
      .flatMap(updateTweet(tweetId, _))
      .flatMap(_.fold(NotFound())(Ok(_)))
  case HEAD -> Root / "tweets" / IntVar(tweetId)      =>
    getTweet(tweetId)
      .flatMap(_.fold(NotFound())(_ => Ok()))
  case DELETE -> Root / "tweets" / IntVar(tweetId)    =>
    deleteTweet(tweetId)
      .flatMap(_ => Ok())
}
