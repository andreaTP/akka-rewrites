package fix.scala213

import metaconfig.Configured
import scalafix.internal.rule.{CompilerTypePrinter, ExplicitResultTypesConfig}
import scalafix.internal.v1.LazyValue
import scalafix.v1._

import scala.meta._
import scala.meta.internal.pc.ScalafixGlobal
import scalafixinternal._
import fix.explicittypes._
import fix.explicittypes.ExplicitImplicitTypes.interestedExplicitResultTypesConfig
import DottyMigrate._

/** This rule combine & run other rules - see [[collector]] - while:
  * + Traveling the doc.tree one time only (for each doc)
  * + Don't re-run [[scala.meta.internal.proxy.GlobalProxy.typedTreeAt]]
  *   for every rules that need `CompilerSupport` */
class DottyMigrate(
    config: ExplicitResultTypesConfig,
    global: LazyValue[ScalafixGlobal]
) extends impl.CompilerDependentRule(global, "fix.scala213.DottyMigrate") {
  def this() = this(ExplicitResultTypesConfig.default, LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    val c = interestedExplicitResultTypesConfig(config)
    val newGlobal = LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, c.symbolReplacements)
    }
    Configured.ok(new DottyMigrate(c, newGlobal))
  }

  protected def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    val power = new DottyPower(global.value, config)
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
  final class DottyPower(
      val g: ScalafixGlobal,
      config: ExplicitResultTypesConfig
  )(implicit val doc: SemanticDocument)
    extends CompilerTypePrinter(g, config) with NullaryOverride.IPower with impl.IPower
}
