val projectName        = "exploring-http4s"
val projectDescription = "Exploring Http4s"

ThisBuild / fork                   := true
ThisBuild / turbo                  := true // default: false
ThisBuild / includePluginResolvers := true // default: false
Global / onChangedBuildSource      := ReloadOnSourceChanges

inThisBuild(
  Seq(
    version                  := Versions.projectVersion,
    scalaVersion             := Versions.scala2Version,
    publish / skip           := true,
    scalacOptions ++= ScalacOptions.defaultScalacOptions,
    semanticdbEnabled        := true,
    semanticdbVersion        := scalafixSemanticdb.revision,
    scalafixDependencies ++= Seq(
      "com.github.liancheng" %% "organize-imports" % Versions.scalafixOrganizeImportsVersion
    ),
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
  .settings(
    name                              := projectName,
    description                       := projectDescription,
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Dependencies.libraryDependencies
  )
