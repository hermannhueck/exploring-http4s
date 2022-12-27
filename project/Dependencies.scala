import sbt._

object Dependencies {

  import Versions._

  lazy val http4sBlazeServer = "org.http4s"      %% "http4s-blaze-server" % Http4sVersion
  lazy val http4sEmberServer = "org.http4s"      %% "http4s-ember-server" % Http4sVersion
  lazy val http4sEmberClient = "org.http4s"      %% "http4s-ember-client" % Http4sVersion
  lazy val http4sDsl         = "org.http4s"      %% "http4s-dsl"          % Http4sVersion
  lazy val http4sCirce       = "org.http4s"      %% "http4s-circe"        % Http4sVersion
  lazy val circeGeneric      = "io.circe"        %% "circe-generic"       % CirceVersion
  lazy val circeLiteral      = "io.circe"        %% "circe-literal"       % CirceVersion
  lazy val munit             = "org.scalameta"   %% "munit"               % MunitVersion
  lazy val scalaCheck        = "org.scalacheck"  %% "scalacheck"          % ScalaCheckVersion
  lazy val munitCE3          = "org.typelevel"   %% "munit-cats-effect-3" % MunitCE3Version
  lazy val logback           = "ch.qos.logback"   % "logback-classic"     % LogbackVersion
  lazy val reactormonk       = "org.reactormonk" %% "cryptobits"          % ReaktormonkVersion
  lazy val swaggerUI         = "org.webjars"      % "swagger-ui"          % SwaggerUIVersion

  // https://github.com/typelevel/kind-projector
  lazy val kindProjectorPlugin    = compilerPlugin(
    compilerPlugin("org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full)
  )
  // https://github.com/oleg-py/better-monadic-for
  lazy val betterMonadicForPlugin = compilerPlugin(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion)
  )

  lazy val libraryDependencies = Seq(
    http4sBlazeServer,
    http4sEmberServer,
    http4sEmberClient,
    http4sDsl,
    http4sCirce,
    circeGeneric,
    circeLiteral,
    reactormonk,
    munit, // use munit also for compiling
    munitCE3 % Test,
    logback  % Runtime,
    // swaggerUI, // for webjar example
    // compiler plugins
    kindProjectorPlugin,
    betterMonadicForPlugin
  ) ++ Seq(
    scalaCheck
  ).map(_ % Test)
}
