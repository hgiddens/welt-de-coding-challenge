package de.welt.hgiddens

import cats.effect.Sync
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

// I've only added a subset of properties here for simplicities sake.
final case class User(id: Int, name: String, email: String)
object User {
  // We're using automatic decoder derivation here which I don't personally
  // like in production code, but it does save a lot of typing.
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, User] =
    jsonOf
}