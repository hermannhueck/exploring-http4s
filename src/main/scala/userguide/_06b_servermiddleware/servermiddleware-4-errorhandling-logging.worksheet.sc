import cats.effect._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.std.Console

import userguide._06b_servermiddleware._

// ===== Error handling and Logging =====

// ----- ErrorAction -----

// Triggers an action if an error occurs while processing the request. Applies to the error channel
// (like IO.raiseError, or MonadThrow[F].raiseError) not http responses that indicate errors (like BadRequest).
// Could be used for logging and monitoring.

import org.http4s.server.middleware.ErrorAction

@annotation.nowarn("cat=unused-params")
val errorActionService = ErrorAction
  .httpRoutes[IO](
    service,
    (req, thr) => Console[IO].println("Oops: " ++ thr.getMessage)
  )
  .orNotFound

val errorActionClient = Client.fromHttpApp(errorActionService)

errorActionClient
  .expect[Unit](boomRequest)
  .attempt
  .unsafeRunSync()

// ----- ErrorHandling -----

// Interprets error conditions into an http response. This will interact with other middleware that handles exceptions,
// like ErrorAction. Different backends might handle exceptions differently, ErrorAction prevents exceptions
// from reaching the backend and thus makes the service more backend-agnostic.

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

import org.http4s.server.middleware.ErrorHandling

val errorHandlingService = ErrorHandling.httpRoutes[IO](service).orNotFound
val errorHandlingClient  = Client.fromHttpApp(errorHandlingService)

// For the first request (the service without ErrorHandling) we have to .attempt to get a value
// that is renderable in this document, for the second request we get a response.

clnt
  .status(boomRequest)
  .attempt
  .unsafeRunSync()
errorHandlingClient
  .status(boomRequest)
  .unsafeRunSync()

// ----- Metrics -----

// Middleware to record service metrics. Requires an implementation of MetricsOps to receive metrics data. Also provided are implementations for Dropwizard and Prometheus metrics.

import org.http4s.server.middleware.Metrics
import org.http4s.metrics.{MetricsOps, TerminationType}

val metricsOps = new MetricsOps[IO] {

  def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
    Console[IO].println("increaseActiveRequests")

  def decreaseActiveRequests(classifier: Option[String]): IO[Unit] =
    IO.unit

  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): IO[Unit] =
    IO.unit

  def recordTotalTime(method: Method, status: Status, elapsed: Long, classifier: Option[String]): IO[Unit] =
    IO.unit

  def recordAbnormalTermination(elapsed: Long, terminationType: TerminationType, classifier: Option[String]): IO[Unit] =
    Console[IO].println(s"abnormalTermination - $terminationType")
}

val metricsService = Metrics[IO](metricsOps)(service).orNotFound
val metricsClient  = Client.fromHttpApp(metricsService)

metricsClient
  .expect[Unit](boomRequest)
  .attempt
  .void
  .unsafeRunSync()
// increaseActiveRequests
// abnormalTermination - Error(java.lang.RuntimeException: boom!)
metricsClient
  .expect[Unit](okRequest)
  .unsafeRunSync()
// increaseActiveRequests

// ----- RequestLogger, ResponseLogger, Logger -----

// Log requests and responses. ResponseLogger logs the responses, RequestLogger logs the request, Logger logs both.

import org.http4s.server.middleware.Logger

val loggerService = Logger
  .httpRoutes[IO](
    logHeaders = false,
    logBody = true,
    redactHeadersWhen = _ => false,
    logAction = Some((msg: String) => Console[IO].println(msg))
  )(service)
  .orNotFound

val loggerClient = Client.fromHttpApp(loggerService)

loggerClient
  .expect[Unit](reverseRequest.withEntity("mood"))
  .unsafeRunSync()
