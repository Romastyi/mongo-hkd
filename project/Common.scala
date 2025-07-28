import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys.*
import sbt.*

object Common {
  val scala213 = "2.13.16"
  val scala36  = "3.6.3"

  sealed trait ScalaVersion
  case object Scala2 extends ScalaVersion
  case object Scala3 extends ScalaVersion

  def byScalaVersion[T](ver: String)(f: ScalaVersion => T): T = CrossVersion.partialVersion(ver) match {
    case Some((3, _)) => f(Scala3)
    case _            => f(Scala2)
  }

  def unmanaged(ver: String, base: File): Seq[File] = byScalaVersion(ver) {
    case Scala2 => Seq(base / "scala-2")
    case Scala3 => Seq(base / "scala-3")
  }

  val baseSettings = Seq(
    scalaVersion       := scala213,
    crossScalaVersions := Seq(scala213, scala36),
    crossVersion       := CrossVersion.binary,
    scalacOptions --= byScalaVersion(scalaVersion.value) {
      case Scala2 => Nil
      case Scala3 => Seq("-Ykind-projector")
    },
    scalacOptions ++= byScalaVersion(scalaVersion.value) {
      case Scala2 =>
        Seq(
          "-Ymacro-annotations",
          "-Xsource:3",
          "-Xsource-features:case-apply-copy-access",
          "-Wconf:msg=unused value of type org.scalatest.Assertion:s",
        )
      case Scala3 =>
        Seq(
          "-Wconf:msg=unused implicit parameter:s",
          "-Wconf:msg=unused value of type org.scalatest.Assertion:s",
          "-Xkind-projector"
        )
    },
    scalafmtOnCompile  := true
  )

  val commonSettings = baseSettings ++ Seq(
    Compile / unmanagedSourceDirectories ++= {
      unmanaged(scalaVersion.value, (Compile / sourceDirectory).value)
    },
    Test / unmanagedSourceDirectories ++= {
      unmanaged(scalaVersion.value, (Test / sourceDirectory).value)
    },
    libraryDependencies ++= byScalaVersion(scalaVersion.value) {
      case Scala2 => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
      case Scala3 => Nil
    }
  )

  def module(moduleName: String): Project = Project(moduleName, file("modules") / moduleName)
    .settings(commonSettings)
    .settings(
      organization := "com.github.romastyi",
      name         := s"mongo-hkd-$moduleName",
      version      := "0.0.1"
    )

}
