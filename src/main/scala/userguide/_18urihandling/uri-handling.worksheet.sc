import org.http4s._
import org.http4s.implicits._

val uri = uri"http://http4s.org"

val docs  = uri.withPath(path"/docs/0.15")
val docs2 = uri / "docs" / "0.15"
assert(docs == docs2)

import org.http4s.UriTemplate._

val template = UriTemplate(
  authority = Some(Uri.Authority(host = Uri.RegName("http4s.org"))),
  scheme = Some(Uri.Scheme.http),
  path = List(PathElm("docs"), PathElm("0.15"))
)

template.toUriIfPossible

// implicit val configuredUri = Configured[String]
//   .flatMap(s => Configured(_ => Uri.fromString(s).toOption))
