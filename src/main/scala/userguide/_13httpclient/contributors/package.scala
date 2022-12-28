package userguide._13httpclient

package object contributors {

  import scala.util.chaining._
  import hutil.stringformat._

  final case class Contributor(login: String, contributions: Int)

  def printQueryResult(user: String, repo: String, result: Either[String, List[Contributor]]): Unit = {

    line80.green pipe println

    result match {
      case Left(error)         =>
        println(s"HTTP request error or JSON parse error: $error".red)
      case Right(contributors) =>
        // printContributors(s"$user/$repo", contributors)
        printContributorsSummary(s"$user/$repo", contributors.size, contributors.map(_.contributions).sum)
        printMostBusyContributor(s"$user/$repo", contributors)
    }

    line80.green pipe println
    System.out.flush()
  }

  def printContributors(repo: String, contributors: List[Contributor]): Unit = {
    s"$line5 Contributors of repo $repo $line10".cyan pipe println
    contributors.foreach { contributor =>
      println(s"Contributor ${contributor.login} made ${contributor.contributions} contributions")
    }
    printContributorsSummary(repo, contributors.size, contributors.map(_.contributions).sum)
  }

  def printMostBusyContributor(repo: String, contributors: List[Contributor]): Unit = {
    val mostBusyContributor = contributors.maxBy(_.contributions)
    s"The most busy contributor of repo $repo is '${mostBusyContributor.login}' with ${mostBusyContributor.contributions} contributions.".cyan pipe println
  }

  def printContributorsSummary(repo: String, contributors: Int, contributions: Int): Unit =
    s"Repo $repo has ${contributors} contributors who made ${contributions} contributions in total.".cyan pipe println

  import io.circe._
  def contributorJson2Contributor(contributorJson: Json): Either[Error, Contributor] = {
    for {
      login         <- contributorJson.hcursor.downField("login").as[String]
      contributions <- contributorJson.hcursor.downField("contributions").as[Int]
    } yield Contributor(login, contributions)
  }
}
