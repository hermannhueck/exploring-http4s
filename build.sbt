import Versions._
import Dependencies._
import ScalacOptions._

val projectName        = "exploring-http4s"
val projectDescription = "Exploring Http4s"

ThisBuild / fork                   := true
ThisBuild / turbo                  := true // default: false
ThisBuild / includePluginResolvers := true // default: false
Global / onChangedBuildSource      := ReloadOnSourceChanges

inThisBuild(
  Seq(
    version                  := projectVersion,
    scalaVersion             := scala2Version,
    publish / skip           := true,
    scalacOptions ++= defaultScalacOptions,
    semanticdbEnabled        := true,
    semanticdbVersion        := scalafixSemanticdb.revision,
    scalafixDependencies ++= Seq("com.github.liancheng" %% "organize-imports" % scalafixOrganizeImportsVersion),
    Test / parallelExecution := false,
    // run 100 tests for each property // -s = -minSuccessfulTests
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-s", "100"),
    initialCommands          :=
      s"""|
          |import scala.util.chaining._
          |import scala.concurrent.duration._
          |println()
          |""".stripMargin // initialize REPL
  )
)

lazy val root = (project in file("."))
  // .dependsOn(hutil)
  .settings(
    name                              := projectName,
    description                       := projectDescription,
    Compile / console / scalacOptions := consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sBlazeServer,
      http4sEmberServer,
      http4sEmberClient,
      http4sDsl,
      http4sCirce,
      circeGeneric,
      munit, // use munit also for compiling
      munitCE3 % Test,
      logback  % Runtime,
      // compiler plugins
      kindProjectorPlugin,
      betterMonadicForPlugin
    ) ++ Seq(
      scalaCheck
    ).map(_ % Test)
  )
