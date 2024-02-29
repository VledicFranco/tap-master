val scala3Version = "3.3.1"

val http4sVersion = "1.0.0-M40"
val log4catsVersion = "2.6.0"
val circeVersion = "0.14.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "tap-master-backend",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.typelevel" %% "log4cats-core" % log4catsVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.0",
      "co.fs2" %% "fs2-core" % "3.9.4",
      "org.scalameta" %% "munit" % "0.7.29" % Test
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
    Compile / run / fork := true,
  )
