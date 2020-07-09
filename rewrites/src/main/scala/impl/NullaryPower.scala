package impl

import scalafix.internal.rule.CompilerException
import scala.annotation.tailrec
import scala.meta.Tree

trait NullaryPower { this: impl.IPower =>
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

  @tailrec private def isNullary(t: g.Type): Boolean = t match {
    case g.NullaryMethodType(_) => true
    case m: g.MethodType => m.isImplicit
    case g.PolyType(_, r) => isNullary(r)
    case _ => false
  }

  def isNullaryMethod(t: Tree): Option[Boolean] = try {
    val meth = gsymbol(t)
    val isJavaDefined = meth.overrideChain.exists(sym => sym.isJavaDefined || sym.owner == g.definitions.AnyClass)

    if (isJavaDefined) None
    else rootOverriddenSymbol(meth) match {
      case m: g.MethodSymbol => Some(isNullary(m.info))
      case _ => None
    }
  } catch {
    case e: Throwable => throw CompilerException(e)
  }
}
