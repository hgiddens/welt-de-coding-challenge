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
  private def userResource(userId: Int): Uri =
    Uri.uri("https://jsonplaceholder.typicode.com/users") / userId.toString
  private def postsResource(userId: Int): Uri =
    Uri.uri("https://jsonplaceholder.typicode.com/posts").withQueryParam("userId", userId.toString)

  // Given the problem statement is to gather data from two endpoints
  // asynchronously, the pervasive references to cats.effect.Sync may be
  // surprising. The Sync typeclass is used to suspend synchronous effects
  // (including failures); it's used by the JSON decoding stuff (Circe) to
  // communicate failures in the type constructor F; failures parsing the JSON
  // don't need to be communicated asynchronously.
  //
  // To demonstrate that the requests to the resources do actually happen
  // asynchronously, I've added some simple logging around the HTTP calls. At
  // least on my machine, this shows as expected that the execution of the
  // client.expect call in user doesn't block the corresponding call in posts,
  // despite happening on the same thread.

  def user[F[_]: Sync](client: Client[F], userId: Int): F[User] =
    for {
      _ <- Sync[F].delay(log.info("Sending user request"))
      user <- client.expect[User](userResource(userId))
      _ <- Sync[F].delay(log.info("Retrieved user"))
    } yield user


  def posts[F[_]: Sync](client: Client[F], userId: Int): F[Posts] =
    for {
      _ <- Sync[F].delay(log.info("Sending posts request"))
      posts <- client.expect[Posts](postsResource(userId))
      _ <- Sync[F].delay(log.info("Retrieved posts"))
    } yield posts

  // To combine the data fetched from the two resources, I've simply chosen to
  // display a summary of the titles of the posts written by the user,
  // including also the user's name and email 
  def combinedMessage(user: User, posts: Posts): String = {
    val header = s"Posts by ${user.name} <${user.email}>"
    val lines = posts.posts.map(post => s"* ${post.title}")
    (header +: lines).mkString("\n")
  }

  // The syntax for providing the parallel evidence is super ugly and
  // unfortunately (citation needed) we can't use existentials to hide the
  // second (applicative) type constructor. For use-cases like this where we
  // just want a very simple, parallel combination of two effects, it's
  // possible to write a simple wrapper type class (I like to call it Par)
  // that "forgets" the applicative effect (G, here) meaning it doesn't have
  // to be specified.
  def showPostSummary[F[_]: Sync, G[_]](client: Client[F], userId: Int)(implicit ev: Parallel[F, G]): F[Unit] =
    for {
      userAndPosts <- (user(client, userId), posts(client, userId)).parTupled
      // This can't be destructured in the previous binding because Scala
      // thinks the pattern match is refutable for some dumb reason. There's
      // actually a compiler plugin that alters the behaviour of for
      // comprehensions to allow this, but introduces a variety of other
      // "entertaining" problems.
      (user, posts) = userAndPosts
      message = combinedMessage(user, posts)
      _ <- Sync[F].delay(println(message))
    } yield ()

  // I'm somewhat ignoring error handling here. Obviously, network
  // communication could fail, or the returned JSON might not be parseable as
  // expected. These errors will be communicated back here via failed IO
  // values embedded in the streams.
  //
  // The benefit of using streams here, rather than having anything to do with
  // streaming responses, is instead simply so that regardless of success or
  // failure we shut down the client's connection pool cleanly.
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    for {
      client <- Http1Client.stream[IO]()
      _ <- Stream.eval(showPostSummary(client, userId = 1))
    } yield ExitCode.Success
}
