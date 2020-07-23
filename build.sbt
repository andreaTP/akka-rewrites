import _root_.scalafix.sbt.BuildInfo._
import sbt.librarymanagement.Configurations.CompilerPlugin

def scalametaVersion = "4.3.20"

inThisBuild(List(
  organization := "com.sandinh",
  licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(Developer("", "", "", url("https://github.com/scala/scala-rewrites/graphs/contributors"))),
  homepage := Some(url("https://github.com/scala/scala-rewrites")),
  scalaVersion := (sys.env.get("TRAVIS_SCALA_VERSION") match {
    case Some("2.13")      => scala213
    case Some("2.12")      => scala212
    case Some("2.12.next") => scala212 // and then overriden by ScalaNightlyPlugin
    case None              => scala213
    case tsv               => sys.error(s"Unknown TRAVIS_SCALA_VERSION $tsv")
  }),
  crossScalaVersions := Seq(scala212, scala213),
  publish / skip := true,
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

val input = project.enablePlugins(ScalaNightlyPlugin).settings(
  scalacOptions ++= List("-Yrangepos", "-P:semanticdb:synthetics:on"),
  libraryDependencies += "org.scalameta" % "semanticdb-scalac" % scalametaVersion % CompilerPlugin cross CrossVersion.patch,
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

val input3 = project.settings(
  scalacOptions ++= List("-Yrangepos", "-P:semanticdb:synthetics:on"),
  addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % scalametaVersion cross CrossVersion.patch),
  scalaVersion := scala213,
  crossScalaVersions := Seq(scalaVersion.value),
  Test / javaSource := (input / Test / javaSource).value,
  Test / sourceGenerators += Def.task {
    val inDir = (input / Test / scalaSource).value
    val outDir = (Test / sourceManaged).value
    val rebase = Path.rebase(inDir, outDir).andThen(_.get)

    // Replace `rule = ..` to `rule = fix.scala213.DottyMigrate` in all .scala files in `inSrcDir`
    def genSrc(in: File): File = {
      val out = rebase(in)
      IO.write(out,
        IO.read(in).replaceFirst(
          """rule\s*=\s*fix\.scala213\.\w+""",
          "rule = fix.scala213.DottyMigrate"))
      out
    }

    val pending = Set(
      "Core", // invalid `?=>` identifier & symbol literal 'foo
      "Infix", // qual() id[Any] qual()
      "NullaryEtaExpansionJava", // https://gitter.im/lampepfl/dotty?at=5f03f931a5ab931e4f6cf6c5
      "Override", // see TODO in fix.scala213.NullaryOverride.collector
      "NullaryOverride",
    )
    def isPending(f: File) = pending.contains(f.base)

    val files = inDir.globRecursive("*.scala").get.filterNot(isPending)

    Tracked.diffOutputs(
      streams.value.cacheStoreFactory.make("diff"),
      FileInfo.lastModified
    )(files.toSet) { diff =>
      IO.delete(diff.removed map rebase)
      val initial = diff.modified & diff.checked
      (initial.map(genSrc) ++ diff.unmodified.map(rebase)).toSeq
    }
  }.taskValue
)

val rewrites213 = rewrites.withId("rewrites213").settings(
  scalaVersion := scala213,
  crossScalaVersions := Seq(scalaVersion.value),
  target := file(s"${target.value.getPath}-2.13"),
  publish / skip := true,
)

val tests3 = project.enablePlugins(ScalafixTestkitPlugin).settings(
  scalaVersion := scala213,
  crossScalaVersions := Seq(scalaVersion.value),
  libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % scalafixVersion % Test cross CrossVersion.patch,
  Compile / compile := (Compile / compile).dependsOn(input3 / Test / compile).value,
  scalafixTestkitInputClasspath          := ( input3 / Test / fullClasspath).value,
  scalafixTestkitInputSourceDirectories  := ( input3 / Test / sourceDirectories).value,
  scalafixTestkitOutputSourceDirectories := {
    val d = (ThisBuild / baseDirectory).value / "output3" / "target" / "src-managed"
    if (!d.exists()) IO.createDirectory(d)
    Seq(d)
  },
).dependsOn(rewrites213)

// This project is used to verify that output can be compiled with dotty
val output3 = project.settings(
  crossScalaVersions := Seq("0.24.0", "0.25.0-RC2"),
  scalaVersion := crossScalaVersions.value.head,
  Test / javaSource := (output / Test / javaSource).value,
  Test / sourceGenerators += Def.task {
    (tests3 / Test / test).value
    (target.value / "src-managed").globRecursive("*.scala").get
  }.taskValue
)

ThisBuild / scalafixScalaBinaryVersion := scalaBinaryVersion.value

lazy val root = project.in(file("."))
  .aggregate(rewrites, input, output, output213, tests, input3, output3, rewrites213)
