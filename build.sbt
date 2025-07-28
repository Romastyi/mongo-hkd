import Common.*
import sbt.*

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.1.0-pekko.RC15"
    ) ++ byScalaVersion(scalaVersion.value) {
      case Scala2 =>
        Seq(
          scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided,
          scalaOrganization.value % "scala-reflect"  % scalaVersion.value % Provided
        )
      case Scala3 => Nil
    } ++ Seq(
      "org.scalatest"              %% "scalatest"                      % "3.2.19" % Test,
      "com.dimafeng"               %% "testcontainers-scala-scalatest" % "0.43.0" % Test,
      "com.dimafeng"               %% "testcontainers-scala-mongodb"   % "0.43.0" % Test,
      "org.apache.logging.log4j"    % "log4j-core"                     % "2.25.1" % Test,
      "org.slf4j"                   % "slf4j-simple"                   % "2.0.17" % Test,
      "com.softwaremill.quicklens" %% "quicklens"                      % "1.9.12" % Test,
    )
  )

lazy val root = (project in file("."))
  .settings(baseSettings)
  .settings(
    name := "mongo-hkd"
  )
  .aggregate(core)
