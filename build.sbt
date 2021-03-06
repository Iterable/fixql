import Dependencies._

inThisBuild(List(
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.10"),
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
    derivation,
  )

lazy val core = Project("fixql-core", file("fixql-core"))
  .settings(name := "fixql-core")
  .settings(libraryDependencies ++= Seq(
    scalaTest % Test,
    "io.higherkindness" %% "droste-core" % "0.8.0",
    "com.graphql-java" % "graphql-java" % "12.0",
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",

    "io.circe" %% "circe-core" % "0.12.3",
    "io.circe" %% "circe-parser" % "0.12.3",
    "io.circe" %% "circe-optics" % "0.12.0",

    "com.typesafe.play" %% "play-json" % "2.7.4",
  ))
  .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0"))

lazy val derivation = Project("fixql-derivation", file("fixql-derivation"))
  .settings(name := "fixql-derivation")
  .settings(
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
      scalaTest % Test,
    )
  )
  .dependsOn(core % "compile->compile;test->test")
