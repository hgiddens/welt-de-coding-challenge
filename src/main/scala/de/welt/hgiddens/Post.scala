package de.welt.hgiddens

import cats.effect.Sync
import io.circe.Decoder
import io.circe.generic.auto._
import org.http4s.circe.jsonOf
import org.http4s.EntityDecoder

// I'd generally prefer to use distinct types for e.g. the user and post IDs.
// However for a toy problem like this, it's ok.
final case class Post(userId: Int, id: Int, title: String, body: String)
final case class Posts(posts: Vector[Post])
object Posts {
  implicit val decoder: Decoder[Posts] =
    Decoder.instance(_.as[Vector[Post]].map(apply))

  // I think in the next release of http4s they've changed it so one no longer
  // has to manually define all these identical entity decoder definitions,
  // but I'm not sure.
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, Posts] =
    jsonOf
}