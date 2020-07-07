package fix.explicittypes

import impl.CompilerDependentRule
import metaconfig.Configured
import scalafix.internal.rule.{TypePrinter, CompilerTypePrinter, ExplicitResultTypesConfig}
import scalafix.internal.v1.LazyValue
import scalafix.patch.Patch
import scalafix.util.TokenOps
import scalafix.v1._

import scala.meta._
import scala.meta.contrib._
import scala.meta.internal.pc.ScalafixGlobal

import ExplicitImplicitTypes._

/** @see [[scalafix.internal.rule.ExplicitResultTypes]] */
final class ExplicitImplicitTypes(
    config: ExplicitResultTypesConfig,
    global: LazyValue[ScalafixGlobal],
) extends CompilerDependentRule(global, "ExplicitImplicitTypes") {
  def this() = this(ExplicitResultTypesConfig.default, LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    val c = interestedExplicitResultTypesConfig(config)
    val newGlobal = LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, c.symbolReplacements)
    }
    Configured.ok(new ExplicitImplicitTypes(c, newGlobal))
  }

  def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    lazy val types = new CompilerTypePrinter(global.value, config)
    doc.tree.collect(collector(types)).asPatch
  }
}

object ExplicitImplicitTypes {
  def interestedExplicitResultTypesConfig(config: Configuration): ExplicitResultTypesConfig = {
    def symbolReplacements(name: String) =
      config.conf.dynamic.selectDynamic(name).symbolReplacements
        .as[Map[String, String]]
        .getOrElse(Map.empty)

    val replacements = Seq(
      "ExplicitResultTypes",
      "ExplicitImplicitTypes",
      "DottyMigrate"
    ).map(symbolReplacements).reduce(_ ++ _)

    // `CompilerTypePrinter` only use these two fields of `ExplicitResultTypesConfig`
    ExplicitResultTypesConfig(
      rewriteStructuralTypesToNamedSubclass = true,
      symbolReplacements = replacements
    )
  }

  def collector(types: => TypePrinter)(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
    case t @ Defn.Val(mods, Pat.Var(name) :: Nil, None, body)
      if isRuleCandidate(t, name, mods, body) =>
      fixDefinition(t, body, name, types)

    case t @ Defn.Var(mods, Pat.Var(name) :: Nil, None, Some(body))
      if isRuleCandidate(t, name, mods, body) =>
      fixDefinition(t, body, name, types)

    case t @ Defn.Def(mods, name, _, _, None, body)
      if isRuleCandidate(t, name, mods, body) =>
      fixDefinition(t, body, name, types)
  }

  // Don't explicitly annotate vals when the right-hand body is a single call
  // to `implicitly`. Prevents ambiguous implicit. Not annotating in such cases,
  // this a common trick employed implicit-heavy code to workaround SI-2712.
  // Context: https://gitter.im/typelevel/cats?at=584573151eb3d648695b4a50
  private def isImplicitly(term: Term): Boolean = term match {
    case Term.ApplyType(Term.Name("implicitly"), _) => true
    case _ => false
  }

  def isRuleCandidate(
     defn: Defn,
     nm: Name,
     mods: Iterable[Mod],
     body: Term
   )(implicit ctx: SemanticDocument): Boolean = {

    def isFinalLiteralVal: Boolean =
      defn.is[Defn.Val] &&
        mods.exists(_.is[Mod.Final]) &&
        body.is[Lit]

    def isImplicit: Boolean =
      mods.exists(_.is[Mod.Implicit]) && !isImplicitly(body)

    def hasParentWihTemplate: Boolean =
      defn.parent.exists(_.is[Template])

    def isLocal: Boolean = nm.symbol.isLocal && !hasParentWihTemplate

    isImplicit && !isFinalLiteralVal && !isLocal
  }

  def fixDefinition(defn: Defn, body: Term, name: Term.Name, types: TypePrinter)(
      implicit ctx: SemanticDocument
  ): Patch = {
    val lst = ctx.tokenList
    for {
      start <- defn.tokens.headOption
      end <- body.tokens.headOption
      // Left-hand side tokens in definition.
      // Example: `val x = ` from `val x = rhs.banana`
      lhsTokens = lst.slice(start, end)
      replace <- lhsTokens.reverseIterator.find(x =>
        !x.is[Token.Equals] && !x.is[Trivia]
      )
      space = {
        if (TokenOps.needsLeadingSpaceBeforeColon(replace)) " "
        else ""
      }
      defnSymbol <- name.symbol.asNonEmpty
      typePatch <- types.toPatch(name.pos, defnSymbol, replace, defn, space)
    } yield typePatch + PatchEmptyBody(body)
  }.asPatch.atomic
}
