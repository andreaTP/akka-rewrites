package fix.scala213

import metaconfig.Configured
import scalafix.internal.v1.LazyValue
import scalafix.v1._

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal
import NullaryOverride._
import scalafix.internal.rule.CompilerException

import scala.PartialFunction.cond
import scala.annotation.tailrec
import scala.meta.tokens.Token.{LeftParen, RightParen}

class NullaryOverride(global: LazyValue[ScalafixGlobal])
    extends impl.CompilerDependentRule(global, "fix.scala213.NullaryOverride") {
  def this() = this(LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[NullaryOverride] =
    Configured.ok(new NullaryOverride(LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, Map.empty)
    }))

  protected def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    val power = new Power(global.value)
    doc.tree.collect(collector(power)).asPatch
  }
}

object NullaryOverride {
  def collector(power: => IPower)(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
    case Defn.Def(_, name, _, Nil, _, _) if power.isNullaryMethod(name).contains(false) =>
      Patch.addRight(name.tokens.last, "()")
    // TODO when remove `()` from `def foo()` in type T then
    // all references `t.foo()` must be rewritten to `t.foo` (not just in this `doc`)
    // Similar, `t.foo _` must be rewritten to `() => t.foo`
    case t @ Defn.Def(_, name, _, List(Nil), _, _) if power.isNullaryMethod(name).contains(true) =>
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
      extends IPower with impl.IPower

  trait IPower { this: impl.IPower =>
    /** Similar to `nextOverriddenSymbol` but loop through ancestors.reverse
      * @see [[scala.reflect.internal.Symbols.Symbol.nextOverriddenSymbol]] */
    private def rootOverriddenSymbol(s: g.Symbol): g.Symbol = {
      import g._, s._
      @tailrec def loop(bases: List[Symbol]): Symbol = bases match {
        case Nil          => NoSymbol
        case base :: rest =>
          val sym = overriddenSymbol(base)
          if (sym == NoSymbol) loop(rest) else sym
      }
      if (isOverridingSymbol) loop(owner.ancestors.reverse) else NoSymbol
    }
    def isNullaryMethod(t: Tree): Option[Boolean] = try {
      val meth = gsymbol(t)
      val isJavaDefined = meth.overrideChain.exists(sym => sym.isJavaDefined || sym.owner == g.definitions.AnyClass)

      if (isJavaDefined) None
      else rootOverriddenSymbol(meth) match {
        case m: g.MethodSymbol => Some(cond(m.info) {
          case g.NullaryMethodType(_) | g.PolyType(_, _: g.NullaryMethodType)=> true
        })
        case _ => None
      }
    } catch {
      case e: Throwable => throw CompilerException(e)
    }
  }
}
