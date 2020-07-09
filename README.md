# Scalafix Rewrites to migrate scala 2 -> 3 (dotty)

## How to migrate project from scala 2 to dotty?

1. Add the `sbt-scalafix` sbt plugin, with the SemanticDB compiler plugin enabled ([official docs][1]):

```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.18")
```

```scala
// build.sbt
inThisBuild(List(
  semanticdbEnabled := true,
  semanticdbOptions += "-P:semanticdb:synthetics:on", // make sure to add this
  semanticdbVersion := scalafixSemanticdb.revision,
  scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
))
```

2. Run `fix.scala213.DottyMigrate` rule ([official docs][2]), in sbt:
_Replace `<version>` with an actual value, eg `0.1.4-sd`. See [releases](https://github.com/ohze/scala-rewrites/releases)_
```scala
> scalafixAll dependency:fix.scala213.DottyMigrate@com.sandinh:scala-rewrites:<version>
```

You can also add the following to your `build.sbt`:

```scala
ThisBuild / scalafixDependencies += "com.sandinh" %% "scala-rewrites" % "<version>"
```

and then:

```scala
> scalafixAll fix.scala213.DottyMigrate
```

3. Run fix.scala213.NullaryOverride rule

echo 'rules = [ fix.scala213.NullaryOverride ]' > .NullaryOverride.conf
echo 'NullaryOverride.mode = Rewrite' >> .NullaryOverride.conf

```scala
> set scalafixConfig := Some(file(".NullaryOverride.conf"))
> scalafixAll
```

[1]: https://scalacenter.github.io/scalafix/docs/users/installation.html
[2]: https://scalacenter.github.io/scalafix/docs/rules/external-rules.html

## To develop/contribute to any of the rewrites

```
sbt ~tests/test
# edit rewrites/src/main/scala/...
```
