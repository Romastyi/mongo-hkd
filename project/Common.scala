import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys._
import sbt._

object Common {
  val scala213 = "2.13.7"
  val scala31  = "3.1.3-RC4" // "3.1.0"

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
    crossScalaVersions := Seq(scala213, scala31),
    crossVersion       := CrossVersion.binary,
    scalacOptions ++= byScalaVersion(scalaVersion.value) {
      case Scala2 => Seq("-Ymacro-annotations")
      case Scala3 => Nil
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
      case Scala2 => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
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
