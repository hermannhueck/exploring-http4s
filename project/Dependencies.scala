import sbt._

object Dependencies {

  import Versions._

  // lazy val http4sBlazeServer = "org.http4s"      %% "http4s-blaze-server" % http4sBlazeVersion
  // lazy val http4sBlazeClient = "org.http4s"      %% "http4s-blaze-client" % http4sBlazeVersion
  lazy val http4sEmberServer = "org.http4s"           %% "http4s-ember-server" % http4sVersion
  lazy val http4sEmberClient = "org.http4s"           %% "http4s-ember-client" % http4sVersion
  lazy val http4sDsl         = "org.http4s"           %% "http4s-dsl"          % http4sVersion
  lazy val http4sCirce       = "org.http4s"           %% "http4s-circe"        % http4sVersion
  lazy val circeGeneric      = "io.circe"             %% "circe-generic"       % circeVersion
  lazy val circeLiteral      = "io.circe"             %% "circe-literal"       % circeVersion
  lazy val circeCore         = "io.circe"             %% "circe-core"          % circeVersion
  lazy val circeParser       = "io.circe"             %% "circe-parser"        % circeVersion
  lazy val ciris             = "is.cir"               %% "ciris"               % cirisVersion
  lazy val cirisCirce        = "is.cir"               %% "ciris-circe"         % cirisVersion
  lazy val munit             = "org.scalameta"        %% "munit"               % munitVersion
  lazy val scalaCheck        = "org.scalacheck"       %% "scalacheck"          % scalaCheckVersion
  lazy val munitCE3          = "org.typelevel"        %% "munit-cats-effect-3" % munitCE3Version
  // lazy val logback           = "ch.qos.logback"   % "logback-classic"     % logbackVersion
  lazy val reactormonk       = "org.reactormonk"      %% "cryptobits"          % reaktormonkVersion
  lazy val swaggerUI         = "org.webjars"           % "swagger-ui"          % swaggerUIVersion
  // lazy val jcdp              = "com.diogonunes"   % "JCDP"                % "2.0.3.1"
  lazy val slf4jApi          = "org.slf4j"             % "slf4j-api"           % slf4jVersion
  lazy val slf4jSimple       = "org.slf4j"             % "slf4j-simple"        % slf4jVersion
  lazy val http4sJwtAuth     = "dev.profunktor"       %% "http4s-jwt-auth"     % http4sJwtAuthVersion
  lazy val jwtCore           = "com.github.jwt-scala" %% "jwt-core"            % jwtScalaVersion
  lazy val jwtCirce          = "com.github.jwt-scala" %% "jwt-circe"           % jwtScalaVersion

  // https://github.com/typelevel/kind-projector
  lazy val kindProjectorPlugin    = compilerPlugin(
    compilerPlugin("org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full)
  )
  // https://github.com/oleg-py/better-monadic-for
  lazy val betterMonadicForPlugin = compilerPlugin(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion)
  )

  lazy val libraryDependencies = Seq(
    // http4sBlazeServer,
    // http4sBlazeClient,
    http4sEmberServer,
    http4sEmberClient,
    http4sDsl,
    http4sCirce,
    circeCore,
    circeParser,
    circeGeneric,
    circeLiteral,
    ciris,
    cirisCirce,
    reactormonk,
    munit, // use munit also for compiling
    munitCE3 % Test,
    slf4jApi,
    slf4jSimple,
    // http4sJwtAuth,
    jwtCore,
    jwtCirce,
    // logback  % Runtime,
    // jcdp     % Runtime,
    // swaggerUI, // for webjar example
    // compiler plugins
    kindProjectorPlugin,
    betterMonadicForPlugin
  ) ++ Seq(
    scalaCheck
  ).map(_ % Test)

  lazy val http4sAuthDependencies = Seq(
    "org.http4s"           %% "http4s-dsl"          % http4sVersion_0_23,
    "org.http4s"           %% "http4s-ember-server" % http4sVersion_0_23,
    "dev.profunktor"       %% "http4s-jwt-auth"     % http4sJwtAuthVersion,
    "com.github.jwt-scala" %% "jwt-core"            % jwtScalaVersion,
    "com.github.jwt-scala" %% "jwt-circe"           % jwtScalaVersion,
    munit, // use munit also for compiling
    munitCE3 % Test,
    slf4jApi,
    slf4jSimple,
    http4sJwtAuth,
    jwtCore,
    jwtCirce,
    // logback  % Runtime,
    // jcdp     % Runtime,
    // swaggerUI, // for webjar example
    // compiler plugins
    kindProjectorPlugin,
    betterMonadicForPlugin
  ) ++ Seq(
    scalaCheck
  ).map(_ % Test)
}
