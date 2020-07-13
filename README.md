# Scalafix Rewrites to migrate scala 2 -> 3 (dotty) | latest scala 2.13.x

## What?
This repo contains the following scalafix rules:

#### fix.scala213.DottyMigrate
Combine and run all following rules when traveling the scalameta trees represent scala files **once**
```hocon
rules = [
  ProcedureSyntax # built-in scalafix rule
  fix.scala213.ConstructorProcedureSyntax
  fix.scala213.ParensAroundLambda
  fix.scala213.NullaryOverride
  fix.explicittypes.ExplicitImplicitTypes
  fix.scala213.MultiArgInfix
  fix.scala213.Any2StringAdd
  fix.scala213.ExplicitNonNullaryApply
  fix.scala213.ExplicitNullaryEtaExpansion
]
```

This rule is [successfully used][3]
to migrate akka project to scala 2.13.3/ scala 3 (dotty)

#### fix.scala213.ConstructorProcedureSyntax
Remove constructor procedure syntax: `def this(..) {..}` => `def this(..) = {..}`

This rule complement to the built-in [ProcedureSyntax][4] rule.

#### fix.scala213.ParensAroundLambda
Fix: parentheses are required around the parameter of a lambda
```scala
Seq(1).map { i: Int => // rewrite to: Seq(1).map { (i: Int) =>
 i + 1
}
Seq(1).map { i => i + 1 } // keep
```

#### fix.scala213.NullaryOverride
Consistent nullary overriding
```scala
trait A {
  def i: Int
  def u(): Unit
}
trait B {
  def i() = 1 // fix by remove `()`: def i = 1
  def u = println("hi") // fix by add `()`: def u() = println("hi")
}
```

#### fix.explicittypes.ExplicitImplicitTypes
Explicitly add type to `implicit def/val/var`s (required by dotty)
```scala
trait Foo {
  // rewrite implicit members of class/trait
  // rewrite to `implicit val s: Seq[String] = Nil`
  implicit val s = Seq.empty[String]
  // rewrite to `implicit def i: Int = 1`
  implicit def i = 1

  def f() = {
    class C {
      // Also rewrite implicit local `def/val/var`s that its parent is a trait/class (required by dotty)
      // rewrite to `implicit val i: Int = 1`
      implicit val i = 1
    }
    // local implicits like this don't need to be rewritten
    implicit val s = ""
    ???
  }
}
```

Optional. Using `symbolReplacements` with this config in `.scalafix.conf`:
```hocon
ExplicitImplicitTypes.symbolReplacements {
  "scala/concurrent/ExecutionContextExecutor#" = "scala/concurrent/ExecutionContext#"
}
```
Then:
```scala
import scala.concurrent.ExecutionContextExecutor

trait T {
  def someEc(): ExecutionContextExecutor
}
trait A {
  def t: T
  // rewrite to `implicit def ec: ExecutionContext = t.someEc()`
  implicit def ec = t.someEc()
}
```

#### fix.scala213.MultiArgInfix
```scala
trait PipeToSupport {
    def to(recipient: Int, sender: Int): Unit
}
def p: PipeToSupport = ???
// rewrite to `p.to(1, 2)
p to (1, 2)
```

#### fix.scala213.FinalObject
Remove redundant `final` modifier for objects:
```scala
final object Abc
```

#### fix.scala213.Any2StringAdd
```scala
Nil + "foo" // => String.valueOf(Nil) + "foo"

type A
def x: A = ???
x + "foo" // => String.valueOf(x) + "foo"

1 + "foo" // => "" + 1 + "foo"
```

#### fix.scala213.ExplicitNonNullaryApply
```scala
// Given:
def meth() = ???
def meth2()(implicit s: String) = ???
object meth3 {
  def apply[A]()(implicit a: A) = ???
}
def prop(implicit s: String) = ???

// Then:
meth // rewrite to `meth()`
meth2 // rewrite to `meth2()`
meth3[Int] // rewrite to: meth3[Int]()
prop // keep
```

#### fix.scala213.ExplicitNullaryEtaExpansion
```scala
def prop         = ""
def meth()       = ""
def void(x: Any) = ""

def def_prop = prop _ // rewrite to: def def_prop = () => prop
def def_meth = meth _ // leave
def def_idty = void _ // leave

def lzy(f: => Int) = {
  val k = f _ // rewrite to: val k = () => f
  ???
}
```

#### Other rules to migrate scala 2.12 -> 2.13
  - fix.scala213.Core
  - fix.scala213.NullaryHashHash
  - fix.scala213.ScalaSeq
  - fix.scala213.Varargs

## How
### Migrate sbt projects to latest scala 2.13 or to dotty
_See also [this commit message][5]_

0. Optional. Add to `.jvmopts`
```
-Xss8m
-Xms1G
-Xmx8G
```

1. Add the `sbt-scalafix` sbt plugin, with the SemanticDB compiler plugin enabled ([official docs][1]):

```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.18-1")
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
_Replace `<version>` with an actual value, eg `0.1.6-sd`. See [releases](https://github.com/ohze/scala-rewrites/releases)_
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

3. Run `fix.scala213.NullaryOverride` rule

echo 'rules = [ fix.scala213.NullaryOverride ]' > .NullaryOverride.conf
echo 'NullaryOverride.mode = Rewrite' >> .NullaryOverride.conf

```scala
> set scalafixConfig := Some(file(".NullaryOverride.conf"))
> scalafixAll
```

4. Optional. Run other rules
```scala
> scalafixAll fix.scala213.FinalObject
> // similar for other rules
```

[1]: https://scalacenter.github.io/scalafix/docs/users/installation.html
[2]: https://scalacenter.github.io/scalafix/docs/rules/external-rules.html
[3]: https://github.com/akka/akka/pull/29367
[4]: https://github.com/scalacenter/scalafix/blob/master/scalafix-rules/src/main/scala/scalafix/internal/rule/ProcedureSyntax.scala
[5]: https://github.com/akka/akka/pull/29367/commits/b4e3f2bd

## To develop/contribute to any of the rewrites

```
sbt ~tests/test
# edit rewrites/src/main/scala/...
```
