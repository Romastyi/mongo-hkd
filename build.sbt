import sbt._

lazy val core = (project in file("modules") / "core")
  .settings(
    name              := "mongo-hkd-core",
    scalaVersion      := "2.13.6",
    scalacOptions += "-Ymacro-annotations",
    scalafmtOnCompile := true,
    libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.0.7",
      "org.scalatest"     %% "scalatest"     % "3.2.9" % Test
    )
  )

lazy val deriving = (project in file("modules") / "deriving")
  .settings(
    name              := "mongo-hkd",
    scalaVersion      := "2.13.6",
    scalacOptions += "-Ymacro-annotations",
    scalafmtOnCompile := true,
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided,
      scalaOrganization.value % "scala-reflect"  % scalaVersion.value % Provided,
      "org.scalatest"        %% "scalatest"      % "3.2.9"            % Test
    )
  )
  .dependsOn(core)

lazy val root = (project in file("."))
  .settings(
    name := "mongo-hkd"
  )
  .aggregate(core, deriving)
