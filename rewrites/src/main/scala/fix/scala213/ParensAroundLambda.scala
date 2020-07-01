package fix.scala213

import scalafix.v1.{Patch, SyntacticDocument, SyntacticRule}
import scala.meta._
import scala.meta.tokens.Token.{LeftParen, KwImplicit}
import ParensAroundLambda._

class ParensAroundLambda extends SyntacticRule("fix.scala213.ParensAroundLambda") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect(collector).asPatch
  }
}

object ParensAroundLambda {
  val collector: PartialFunction[Tree, Patch] = {
    case Term.Function(List(p @ Term.Param(_, _, Some(_), _)), _) =>
      val tokens = p.tokens
      val head = tokens.head
      if (head.is[LeftParen] || head.is[KwImplicit]) Patch.empty
      else {
        Patch.addLeft(head, "(") +
          Patch.addRight(tokens.last, ")")
      }
  }
}
