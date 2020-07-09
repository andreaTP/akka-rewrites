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
    config: DottyMigrateConfig,
    global: LazyValue[ScalafixGlobal]
) extends impl.CompilerDependentRule(global, "fix.scala213.DottyMigrate") {
  def this() = this(DottyMigrateConfig.default, LazyValue.later(() => ScalafixGlobal.newCompiler(Nil, Nil, Map.empty)))

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    val c = DottyMigrateConfig(
      interestedExplicitResultTypesConfig(config),
      config.conf.get[NullaryOverrideConfig]("NullaryOverride").getOrElse(this.config.nullaryOverride)
    )
    val newGlobal = LazyValue.later { () =>
      ScalafixGlobal.newCompiler(config.scalacClasspath, config.scalacOptions, c.resTypes.symbolReplacements)
    }
    Configured.ok(new DottyMigrate(c, newGlobal))
  }

  protected def unsafeFix()(implicit doc: SemanticDocument): Patch = {
    val power = new DottyPower(global.value, config.resTypes)
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
      NullaryOverride.collector(power, config.nullaryOverride),
      new ExplicitNonNullaryApply(global).collector(power),
      ExplicitImplicitTypes.collector(power),
    ).map(_.lift.andThen(_.getOrElse(Patch.empty)))

    {
      case t => all.foldLeft(Patch.empty)(_ + _(t))
    }
  }

  override def afterComplete(): Unit = {
    config.nullaryOverride.saveCollected()
    super.afterComplete()
  }
}

object DottyMigrate {
  final class DottyPower(
      val g: ScalafixGlobal,
      config: ExplicitResultTypesConfig
  )(implicit val doc: SemanticDocument)
    extends CompilerTypePrinter(g, config) with impl.NullaryPower with impl.IPower
}

case class DottyMigrateConfig(
    resTypes: ExplicitResultTypesConfig = ExplicitResultTypesConfig.default,
    nullaryOverride: NullaryOverrideConfig = NullaryOverrideConfig.default
)
object DottyMigrateConfig {
  val default = DottyMigrateConfig()
}
