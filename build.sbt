import Common._
import sbt._

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.1.0-RC6"
    ) ++ byScalaVersion(scalaVersion.value) {
      case Scala2 =>
        Seq(
          scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided,
          scalaOrganization.value % "scala-reflect"  % scalaVersion.value % Provided
        )
      case Scala3 => Nil
    } ++ Seq(
      "org.scalatest"              %% "scalatest"                      % "3.2.14"  % Test,
      "com.dimafeng"               %% "testcontainers-scala-scalatest" % "0.40.11" % Test,
      "com.dimafeng"               %% "testcontainers-scala-mongodb"   % "0.40.11" % Test,
      "org.apache.logging.log4j"    % "log4j-core"                     % "2.19.0"  % Test,
      "org.slf4j"                   % "slf4j-simple"                   % "2.0.3"   % Test,
      "com.softwaremill.quicklens" %% "quicklens"                      % "1.8.10"  % Test
    )
  )

lazy val root = (project in file("."))
  .settings(baseSettings)
  .settings(
    name := "mongo-hkd"
  )
  .aggregate(core)
