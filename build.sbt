name := "hgiddens-welt-de-coding-challenge"

scalaVersion := "2.12.6"
scalacOptions ++= Seq(
  "-language:higherKinds",
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-generic" % "0.9.3",
  "org.http4s" %% "http4s-blaze-client" % "0.18.15",
  "org.http4s" %% "http4s-circe" % "0.18.15",
  "org.slf4j" % "slf4j-simple" % "1.7.25" % Runtime,
)
