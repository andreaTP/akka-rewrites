package fix.scala213

import metaconfig.Configured
import scalafix.internal.v1.LazyValue
import scalafix.v1._

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal
import scala.meta.tokens.Token.{LeftParen, RightParen}

import impl.{IPower, NullaryPower}
import NullaryOverride._
import NullaryOverrideMode._

class NullaryOverride(
    config: NullaryOverrideConfig,
    global: LazyValue[ScalafixGlobal]
) extends impl.CompilerDependentRule(global, "fix.scala213.NullaryOverride") {
  def this() = this(NullaryOverrideConfig.default, LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[NullaryOverride] = {
    val newGlobal = LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, Map.empty)
    }
    config.conf
      .getOrElse("NullaryOverride")(NullaryOverrideConfig.default)
      .map(new NullaryOverride(_, newGlobal))
  }

  override def afterComplete(): Unit = {
    config.saveCollected()
    super.afterComplete()
  }

  protected def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    val power = new Power(global.value)
    doc.tree.collect(collector(power, config)).asPatch
  }
}

/** @note When remove `()` from `def foo()` in type T then
  *       all references `t.foo()` must be rewritten to `t.foo` (not just in this `doc`)
  *       Similar, `t.foo _` must be rewritten to `() => t.foo` */
object NullaryOverride {
  def collector(
       power: => NullaryPower,
       config: NullaryOverrideConfig
  )(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = config.mode match {
    case ResetAndCollect | CollectAppend => {
      case Defn.Def(_, name, _, Nil, _, _) if power.isNullaryMethod(name).contains(false) =>
        config.nonNullarySymbols += name.symbol.value
        Patch.empty
      case Defn.Def(_, name, _, List(Nil), _, _) if power.isNullaryMethod(name).contains(true) =>
        config.nullarySymbols += name.symbol.value
        Patch.empty
    }
    case Rewrite => {
      case config.nonNullaryMatcher(name: Term.Name)
        if name.isReference && !isApply(name) =>
        Patch.addRight(name, "()")
      case config.nonNullaryMatcher(Defn.Def(_, name, _, Nil, _, _)) =>
        Patch.addRight(name, "()")
      case config.nullaryMatcher(t: Defn.Def) =>
        removeParens(t, t.name)
      case config.nullaryMatcher(t@Term.Apply(fun, _)) =>
        removeParens(t, fun)
    }
  }

  private def isApply(name: Term.Name): Boolean = name.parent match {
    case Some(_: Term.Apply) => true
    case Some(s @ Term.Select(_, `name`)) => s.parent.exists(_.isInstanceOf[Term.Apply])
    case _ => false
  }

  private def removeParens(t: Tree, name: Term) = {
    val lastNameTok = name.tokens.last
    val tail = t.tokens.dropWhile(_ != lastNameTok)
    // '(' and ')' and all trivial token between those parens
    val parens = tail.slice(
      tail.indexWhere(_.is[LeftParen]),
      tail.indexWhere(_.is[RightParen]) + 1,
    )
    Patch.removeTokens(parens)
  }

  final class Power(val g: ScalafixGlobal)(implicit val doc: SemanticDocument)
      extends NullaryPower with IPower
}
