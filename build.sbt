import java.nio.file.Paths
import java.util.regex.Pattern

import _root_.scalafix.sbt.BuildInfo._
import _root_.scalafix.internal.sbt.{Arg, LoggingOutputStream, ScalafixInterface}
import _root_.scalafix.sbt.ScalafixFailed
import sbt.io.PathFinder
import sbt.librarymanagement.Configurations.CompilerPlugin
import sbt.io.ExtensionFilter.ScalaOrJavaSource
import scala.meta.io.AbsolutePath
import scala.util.matching.Regex

def scalametaVersion = "4.3.18"

inThisBuild(List(
  organization := "org.scala-lang",
  licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(Developer("", "", "", url("https://github.com/scala/scala-rewrites/graphs/contributors"))),
  homepage := Some(url("https://github.com/scala/scala-rewrites")),
  scalaVersion := (sys.env.get("TRAVIS_SCALA_VERSION") match {
    case Some("2.13")      => scala213
    case Some("2.12")      => scala212
    case Some("2.12.next") => scala212 // and then overriden by ScalaNightlyPlugin
    case None              => scala213 //scala212
    case tsv               => sys.error(s"Unknown TRAVIS_SCALA_VERSION $tsv")
  }),
  crossScalaVersions := Seq(scala212, scala213),
  publish / skip := true,
  scalafixScalaBinaryVersion := scalaBinaryVersion.value,
))


val rewrites = project.enablePlugins(ScalaNightlyPlugin).settings(
  moduleName := "scala-rewrites",
  libraryDependencies += "ch.epfl.scala" %% "scalafix-rules" % scalafixVersion,
  libraryDependencies ++= (
    if (scalaVersion.value.startsWith("2.13")) Nil
    else Seq("com.github.bigwheel" %% "util-backports" % "2.1")
  ),
  publish / skip := false,
)

val Migrate = config("Migrate") extend Test
val scalafixInterface = taskKey[ScalafixInterface]("scalafixInterface")

val input = project.enablePlugins(ScalaNightlyPlugin).configs(Migrate).dependsOn(rewrites % "scalafix").settings(
  scalacOptions ++= List("-Yrangepos", "-P:semanticdb:synthetics:on"),
  libraryDependencies += "org.scalameta" % "semanticdb-scalac" % scalametaVersion % CompilerPlugin cross CrossVersion.patch,
  inConfig(Migrate)(Defaults.configSettings ++ scalafixConfigSettings(Migrate)),
  Migrate / scalafixConfig := Some(baseDirectory.value / ".NullaryOverride.conf"),
  scalafixInterface := {
    ScalafixInterface
    .fromToolClasspath("2.13", Nil, scalafixResolvers.value)()
    .withArgs(
      Arg.ScalaVersion(scalaVersion.value),
      Arg.ScalacOptions((Test / scalacOptions).value),
      Arg.ToolClasspath((rewrites / Compile / fullClasspath).value.map(_.data.asURL), Nil, Nil),
    )
  },
  Migrate / sourceGenerators += Def.taskDyn {
    if (!scalaVersion.value.startsWith("2.13")) Def.task(Seq.empty[File])
    else Def.task {
      val srcDirs = (Test / sourceDirectories).value
      val outDir = (Migrate / managedSourceDirectories).value.head

      val pending = Set(
        "Core", // invalid `?=>` identifier & symbol literal 'foo
        "Infix", // qual() id[Any] qual()
        "NullaryEtaExpansionJava", // https://gitter.im/lampepfl/dotty?at=5f03f931a5ab931e4f6cf6c5
      )
      def notPending(f: File) = !pending.contains(f.base)

//      val files = (Test / sources).value.filter(notPending)
      val cp = (Test / fullClasspath).value.map(_.data.toPath)

      val pairs = srcDirs.flatMap { d =>
        PathFinder(d).globRecursive(ScalaOrJavaSource).filter(notPending) pair Path.rebase(d, outDir)
      }
      val interface = scalafixInterface.value

      val outSrcs = pairs.map(_._2)
      if (!outSrcs.forall(_.exists())) {
        val errors = interface
          .withArgs(
            Arg.Rules(Seq("fix.scala213.DottyMigrate")),
            Arg.Classpath(cp),
            Arg.Paths(pairs.map(_._1.toPath)),
            Arg.ParsedArgs(Seq(
              "--out-from", srcDirs.map(d => Regex.quote(d.getPath)).mkString("|"),
              "--out-to", outDir.getPath)),
          ).run()

        if (errors.nonEmpty) throw new ScalafixFailed(errors.toList)

        IO.copy(pairs)
      }
//      outDir.globRecursive(ScalaOrJavaSource).get
      pairs.map(_._2)
    }
  }.taskValue,
)

val output = project

// This project is used to verify that the output code actually compiles with scala 2.13
val output213 = output.withId("output213").settings(
  target := file(s"${target.value.getPath}-2.13"),
  scalaVersion := scala213,
  crossScalaVersions := Seq(scalaVersion.value),
)

val tests = project.dependsOn(rewrites).enablePlugins(ScalaNightlyPlugin, ScalafixTestkitPlugin).settings(
  libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % scalafixVersion % Test cross CrossVersion.patch,
  Compile / compile := (Compile / compile).dependsOn(input / Test / compile).value,
  scalafixTestkitInputClasspath          := ( input / Test / fullClasspath).value,
  scalafixTestkitInputSourceDirectories  := ( input / Test / sourceDirectories).value,
  scalafixTestkitOutputSourceDirectories := (output / Test / sourceDirectories).value,
  ScalaNightlyPlugin.ifNightly(Test / fork := true),
)

ScalaNightlyPlugin.bootstrapSettings

// This project is used to verify that output can be compiled with dotty
val output3 = project.settings(
  crossScalaVersions := Seq("0.24.0", "0.25.0-RC2"),
  scalaVersion := crossScalaVersions.value.head,
  Test / sources := (input / Migrate / sources).value,
//  Test / test := (Test / test).dependsOn((input / Migrate / scalafix).toTask("")).value
  Test / test := {
    (input / Migrate / scalafix).toTask("").value
    (Test / test).value
  }
)
