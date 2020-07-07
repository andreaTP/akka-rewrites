package fix.scala213

import metaconfig.Configured
import scalafix.internal.v1.LazyValue
import scalafix.v1._

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal
import DottyMigrate._
import scalafixinternal._
import fix.explicittypes._

/** This rule combine & run other rules - see [[collector]] - while:
  * + Traveling the doc.tree one time only (for each doc)
  * + Don't re-run [[scala.meta.internal.proxy.GlobalProxy.typedTreeAt]]
  *   for every rules that need `CompilerSupport` */
class DottyMigrate(global: LazyValue[ScalafixGlobal])
    extends impl.CompilerDependentRule(global, "fix.scala213.DottyMigrate") {
  def this() = this(LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[DottyMigrate] = {
    def getSymbolReplacements(configName: String) =
      config.conf.dynamic.selectDynamic(configName).symbolReplacements
      .as[Map[String, String]]
      .getOrElse(Map.empty)

    val symbolReplacements = Seq(
      "DottyMigrate",
      "ExplicitImplicitTypes"
    ).map(getSymbolReplacements).reduce(_ ++ _)

    Configured.ok(new DottyMigrate(LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, symbolReplacements)
    }))
  }

  protected def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    val power = new DottyPower(global.value)
    doc.tree.collect(collector(power)).asPatch
  }

  // TODO add other rules:
  // + symbol literal
  // Note: When combining other rule that need `CompilerSupport`,
  // eg `ExplicitResultTypes`, we will make `DottyPower` extends the trait
  // that implement CompilerSupport logic for that rule.
  def collector(power: DottyPower)(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
    val all = Seq(
      ConstructorProcedureSyntax.collector,
      ProcedureSyntax.collector,
      ParensAroundLambda.collector,
      new Any2StringAdd().collector,
      new ExplicitNullaryEtaExpansion().collector,
      NullaryOverride.collector(power),
      new ExplicitNonNullaryApply(global).collector(power),
      ExplicitImplicitTypes.collector(power),
    ).map(_.lift.andThen(_.getOrElse(Patch.empty)))

    {
      case t => all.foldLeft(Patch.empty)(_ + _(t))
    }
  }
}

object DottyMigrate {
  final class DottyPower(g: ScalafixGlobal)(implicit doc: SemanticDocument)
      extends CompilerTypePrinter(g) with NullaryOverride.IPower with impl.IPower
}
