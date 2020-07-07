package scalafixinternal

import scala.meta._
import scalafix.v1._
import scalafix.util.Trivia

/** same as [[scalafix.internal.rule.ProcedureSyntax]] */
object ProcedureSyntax {
  def collector(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
    case t: Decl.Def if t.decltpe.tokens.isEmpty =>
      Patch.addRight(t.tokens.last, s": Unit").atomic
    case t: Defn.Def
        if t.decltpe.exists(_.tokens.isEmpty) &&
          t.body.tokens.head.is[Token.LeftBrace] =>
      val fixed = for {
        bodyStart <- t.body.tokens.headOption
        toAdd <- doc.tokenList.leading(bodyStart).find(!_.is[Trivia])
      } yield Patch.addRight(toAdd, s": Unit =").atomic
      fixed.getOrElse(Patch.empty)
  }
}
