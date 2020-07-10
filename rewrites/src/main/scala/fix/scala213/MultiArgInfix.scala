package fix.scala213

import scalafix.v1._

import scala.meta._
import MultiArgInfix._

import scala.meta.tokens.Token.{LeftBracket, LeftParen, RightBracket}
import scala.meta.tokens.Tokens

class MultiArgInfix extends SyntacticRule("fix.scala213.MultiArgInfix") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect(collector).asPatch
  }
}

object MultiArgInfix {
  implicit class TokensOps(private val tokens: Tokens) extends AnyVal {
    def right(t: Token): Tokens = tokens.dropWhile(_ != t).drop(1)
    def right(p: Token => Boolean): Tokens = tokens.dropWhile(!p(_)).drop(1)
  }

  val collector: PartialFunction[Tree, Patch] = {
    case t @ Term.ApplyInfix(lhs, op, targs, args) if args.sizeIs > 1 =>
      val tokens = t.tokens
      val opTokens = op.tokens
      val toRemoveBetweenOpAndArgs = targs match {
        case Nil => tokens.right(opTokens.last).takeWhile(!_.is[LeftParen])
        case _ =>
          tokens.right(opTokens.last).takeWhile(!_.is[LeftBracket]) ++
            tokens.right(targs.last.tokens.last).right(_.is[RightBracket]).takeWhile(!_.is[LeftParen])
      }
      Patch.removeTokens(
        tokens.right(lhs.tokens.last).takeWhile(_ != opTokens.head) ++
          toRemoveBetweenOpAndArgs
      ) +
        Patch.addRight(lhs, ".")
  }
}
