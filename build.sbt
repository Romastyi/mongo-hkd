import sbt._

lazy val core = (project in file("modules") / "core")
  .settings(
    name              := "mongo-hkd-core",
    scalaVersion      := "2.13.7",
    scalacOptions += "-Ymacro-annotations",
    scalafmtOnCompile := true,
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.0.7",
      "org.scalatest"     %% "scalatest"     % "3.2.9" % Test,
      compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
    )
  )

lazy val deriving = (project in file("modules") / "deriving")
  .settings(
    name              := "mongo-hkd",
    scalaVersion      := "2.13.6",
    scalacOptions += "-Ymacro-annotations",
    scalafmtOnCompile := true,
    libraryDependencies ++= Seq(
      scalaOrganization.value       % "scala-compiler"                 % scalaVersion.value % Provided,
      scalaOrganization.value       % "scala-reflect"                  % scalaVersion.value % Provided,
      "org.scalatest"              %% "scalatest"                      % "3.2.9"            % Test,
      "com.dimafeng"               %% "testcontainers-scala-scalatest" % "0.39.12"          % Test,
      "com.dimafeng"               %% "testcontainers-scala-mongodb"   % "0.39.12"          % Test,
      "org.apache.logging.log4j"    % "log4j-core"                     % "2.14.1"           % Test,
      "org.slf4j"                   % "slf4j-simple"                   % "1.7.32"           % Test,
      "com.softwaremill.quicklens" %% "quicklens"                      % "1.7.5"            % Test
    )
  )
  .dependsOn(core)

lazy val root = (project in file("."))
  .settings(
    name := "mongo-hkd"
  )
  .aggregate(core, deriving)
