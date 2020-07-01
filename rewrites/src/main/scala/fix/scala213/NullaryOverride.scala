package fix.scala213

import metaconfig.Configured
import scalafix.internal.v1.LazyValue
import scalafix.v1._

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal
import NullaryOverride._
import scalafix.internal.rule.CompilerException

import scala.PartialFunction.cond

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
    case t @ Defn.Def(_, name, _, List(Nil), _, _) if power.isNullaryMethod(name).contains(true) =>
      val nameTok = name.tokens.last
      val parens = t.tokens.dropWhile(_ != nameTok).slice(1, 3) // '(' and ')'
      Patch.removeTokens(parens)
  }

  final class Power(val g: ScalafixGlobal)(implicit val doc: SemanticDocument)
      extends IPower with impl.IPower

  trait IPower { this: impl.IPower =>
    def isNullaryMethod(t: Tree): Option[Boolean] = try {
      val meth = gsymbol(t)
      val isJavaDefined = meth.overrideChain.exists(sym => sym.isJavaDefined || sym.owner == g.definitions.AnyClass)

      if (isJavaDefined) None
      else meth.nextOverriddenSymbol match {
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
