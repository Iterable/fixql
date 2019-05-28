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
  ))
