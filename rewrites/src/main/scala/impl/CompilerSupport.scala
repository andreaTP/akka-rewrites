package impl

import scalafix.v1.SemanticDocument

import scala.collection.concurrent.TrieMap
import scala.meta.inputs.Position
import scala.meta.internal.pc.ScalafixGlobal
import scala.meta.internal.proxy.GlobalProxy

trait CompilerSupport {
  val g: ScalafixGlobal
  implicit val doc: SemanticDocument

  protected lazy val unit: g.CompilationUnit = g.newCompilationUnit(doc.input.text, doc.input.syntax)

  private val typedTreeCalled = TrieMap.empty[Int, g.Tree] // key is Position.start

  def typedTreeAt(pos: Position): g.Tree = typedTreeCalled.getOrElseUpdate(
    pos.start,
    GlobalProxy.typedTreeAt(g, unit.position(pos.start))
  )
}
