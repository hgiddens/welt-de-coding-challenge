package de.welt.hgiddens

import cats.Parallel
import cats.effect.{IO, Sync}
import cats.implicits._
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.log4s.getLogger

object App extends StreamApp[IO] {
  private[this] val log = getLogger
  private val userResource = Uri.uri("https://jsonplaceholder.typicode.com/users/1")
  private val postsResource = Uri.uri("https://jsonplaceholder.typicode.com/posts?userId=1")

  def user[F[_]: Sync](client: Client[F]): F[User] =
    for {
      _ <- Sync[F].delay(log.info("Sending user request"))
      user <- client.expect[User](userResource)
      _ <- Sync[F].delay(log.info("Retrieved user"))
    } yield user


  def posts[F[_]: Sync](client: Client[F]): F[Posts] =
    for {
      _ <- Sync[F].delay(log.info("Sending posts request"))
      posts <- client.expect[Posts](postsResource)
      _ <- Sync[F].delay(log.info("Retrieved posts"))
    } yield posts

  def combinedMessage(user: User, posts: Posts): String = {
    val header = s"Posts by ${user.name} <${user.email}>"
    val lines = posts.posts.map(post => s"* ${post.title}")
    (header +: lines).mkString("\n")
  }

  // The syntax for providing the parallel evidence is super ugly and
  // unfortunately (citation needed) we can't use existentials to hide the
  // second (applicative) type constructor. For use-cases like this where we just want a
  // very simple, parallel combination of two effects, it's possible to write
  // a simple wrapper type class (I like to call it Par) that "forgets" the
  // applicative effect (G, here) meaning it doesn't have to be specified.
  def combine[F[_]: Sync, G[_]](client: Client[F])(implicit ev: Parallel[F, G]): F[Unit] =
    for {
      userAndPosts <- Parallel.parTuple2(user(client), posts(client))
      (user, posts) = userAndPosts
      message = combinedMessage(user, posts)
      _ <- Sync[F].delay(println(message))
    } yield ()

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    for {
      client <- Http1Client.stream[IO]()
      _ <- Stream.eval(combine(client))
    } yield ExitCode.Success
}
