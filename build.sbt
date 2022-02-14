import Common._
import sbt._

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.1.0-RC3"
    ) ++ byScalaVersion(scalaVersion.value) {
      case Scala2 =>
        Seq(
          scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided,
          scalaOrganization.value % "scala-reflect"  % scalaVersion.value % Provided
        )
      case Scala3 => Nil
    } ++ Seq(
      "org.scalatest"              %% "scalatest"                      % "3.2.9"   % Test,
      "com.dimafeng"               %% "testcontainers-scala-scalatest" % "0.39.12" % Test,
      "com.dimafeng"               %% "testcontainers-scala-mongodb"   % "0.39.12" % Test,
      "org.apache.logging.log4j"    % "log4j-core"                     % "2.17.0"  % Test,
      "org.slf4j"                   % "slf4j-simple"                   % "1.7.32"  % Test,
      "com.softwaremill.quicklens" %% "quicklens"                      % "1.8.2"   % Test
    )
  )

lazy val root = (project in file("."))
  .settings(baseSettings)
  .settings(
    name := "mongo-hkd"
  )
  .aggregate(core)
