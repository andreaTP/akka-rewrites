package fix.scala213

import java.io.FileWriter
import java.nio.file.{Path, Paths}
import scala.io.Source
import scala.collection.mutable
import metaconfig._
import metaconfig.generic.Surface
import scalafix.internal.config.ReaderUtil
import scalafix.v1.SymbolMatcher
import scala.util.Using
import NullaryOverrideConfig._
import NullaryOverrideMode._

case class NullaryOverrideConfig(
    mode: NullaryOverrideMode = CollectAppend,
    nullarySymPath: Path = Paths.get(".nullary.NullaryOverride"),
    nonNullarySymPath: Path = Paths.get(".nonNullary.NullaryOverride")
) {
  val nullarySymbols: mutable.Set[String] = mutable.Set.empty
  val nonNullarySymbols: mutable.Set[String] = mutable.Set.empty
  lazy val nullaryMatcher: SymbolMatcher = matcher(nullarySymPath)
  lazy val nonNullaryMatcher: SymbolMatcher = matcher(nonNullarySymPath)

  def saveCollected(): Unit = mode match {
    case ResetAndCollect =>
      writeSymbols(nullarySymbols, nullarySymPath)
      writeSymbols(nonNullarySymbols, nonNullarySymPath)
    case CollectAppend =>
      appendSymbols(nullarySymbols, nullarySymPath)
      appendSymbols(nonNullarySymbols, nonNullarySymPath)
    case Rewrite => // do nothing
  }
}

object NullaryOverrideConfig {
  val default: NullaryOverrideConfig = NullaryOverrideConfig()
  implicit val reader: ConfDecoder[NullaryOverrideConfig] = generic.deriveDecoder[NullaryOverrideConfig](default)
  implicit val surface: Surface[NullaryOverrideConfig] = generic.deriveSurface[NullaryOverrideConfig]

  private def readLines(p: Path): List[String] =
    Using(Source.fromFile(p.toFile))(_.getLines.toList).getOrElse(Nil)
  private def writeSymbols(symbols: Iterable[String], p: Path): Unit =
    Using(new FileWriter(p.toFile)) { fw =>
      symbols.foreach { sym => fw.write(s"$sym\n") }
    }.get
  private def appendSymbols(symbols: Iterable[String], p: Path): Unit =
    writeSymbols(readLines(p).toSet ++ symbols, p)

  private def matcher(p: Path) = SymbolMatcher.exact(readLines(p): _*)
}

sealed trait NullaryOverrideMode
object NullaryOverrideMode {
  case object ResetAndCollect extends NullaryOverrideMode
  case object CollectAppend extends NullaryOverrideMode
  case object Rewrite extends NullaryOverrideMode

  private def all = Seq(ResetAndCollect, CollectAppend, Rewrite)
  implicit val reader: ConfDecoder[NullaryOverrideMode] =
    ReaderUtil.fromMap(all.map(x => x.toString -> x).toMap)
}
