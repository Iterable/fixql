import Dependencies._

inThisBuild(List(
  scalaVersion := "2.12.8",
  version := "0.1.0-SNAPSHOT",
  organization := "com.iterable",
  organizationName := "Iterable",
  homepage := Some(url("https://github.com/Iterable/fixql")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "tksfz",
      "Thomas Kim",
      "tk@iterable.com",
      url("https://www.iterable.com")
    )
  )
))

lazy val root = Project("fixql", file("."))
  .settings(name := "fixql")
  .aggregate(
    core,
  )

lazy val core = Project("fixql-core", file("fixql-core"))
  .settings(name := "fixql-core")
  .settings(libraryDependencies ++= Seq(
    scalaTest % Test,
    "io.higherkindness" %% "droste-core" % "0.6.0",
    "com.graphql-java" % "graphql-java" % "12.0",
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",

    "io.circe" %% "circe-core" % "0.9.3",
    "io.circe" %% "circe-parser" % "0.9.3",
    "io.circe" %% "circe-optics" % "0.9.3",

    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.typesafe.play" %% "play-json" % "2.7.1",

    "com.h2database" % "h2" % "1.4.187" % Test,
  ))
