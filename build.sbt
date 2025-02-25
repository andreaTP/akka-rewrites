import _root_.scalafix.sbt.BuildInfo.{ scala212, scalafixVersion }

inThisBuild(List(
  organization := "com.sandinh",
  licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(Developer("", "", "", url("https://github.com/scala/scala-rewrites/graphs/contributors"))),
  homepage := Some(url("https://github.com/scala/scala-rewrites")),
  scalaVersion := scala212,
))

skip in publish := true

lazy val publishSettings = Seq(
  publishTo := sonatypePublishToBundle.value,
  publishMavenStyle := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ohze/scala-rewrites"),
      "scm:git@github.com:ohze/scala-rewrites.git"
    )
  )
)

val rewrites = project.in(file("scalafix/rules")).settings(
  moduleName := "scala-rewrites",
  libraryDependencies += "ch.epfl.scala" %% "scalafix-rules" % scalafixVersion,
)
  .settings(publishSettings)

val input = project.settings(
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions ++= List("-Yrangepos", "-P:semanticdb:synthetics:on"),
  skip in publish := true,
)

val output = project.settings(skip in publish := true)

val output213 = output.withId("output213").settings(
  target := (target.value / "../target-2.13").getCanonicalFile,
  scalaVersion := "2.13.0",
)

val tests = project.dependsOn(rewrites).enablePlugins(ScalafixTestkitPlugin).settings(
  skip in publish := true,
  libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % scalafixVersion % Test cross CrossVersion.full,
  compile in Compile := (compile in Compile).dependsOn(compile in (input, Compile)).value,
  scalafixTestkitOutputSourceDirectories := (sourceDirectories in (output, Compile)).value,
  scalafixTestkitInputSourceDirectories := (sourceDirectories in (input, Compile)).value,
  scalafixTestkitInputClasspath := (fullClasspath in (input, Compile)).value,
)
