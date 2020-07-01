package impl

import scalafix.internal.rule.CompilerException
import scalafix.internal.v1.LazyValue
import scalafix.v1.{Patch, SemanticDocument, SemanticRule}

import scala.meta.inputs.Input
import scala.meta.internal.pc.ScalafixGlobal
import scala.util.control.Exception.nonFatalCatch

abstract class CompilerDependentRule(global: LazyValue[ScalafixGlobal], name: String) extends SemanticRule(name) {
  protected def unsafeFix()(implicit doc: SemanticDocument): Patch

  override def fix(implicit doc: SemanticDocument): Patch = {
    try unsafeFix() catch {
      case e: CompilerException =>
        println(s"[info] retry fix $name on ${path(doc.input)}: $e")
        // Give it another shot (good old "retry.retry")
        // as the presentation compiler sometimes just dies and succeeds the next time...
        shutdownAndResetCompiler()
        try unsafeFix() catch {
          case e: CompilerException =>
            println(s"[error] abort fix $name on ${path(doc.input)}: $e")
            e.printStackTrace()
            // Give up on fixing this file as compiling it crashed the (presentation) compiler twice
            // but first reset the state of the compiler for the next file
            shutdownAndResetCompiler()
            Patch.empty
        }
    }
  }

  override def afterComplete(): Unit = shutdownAndResetCompiler()

  def shutdownAndResetCompiler(): Unit = {
    for (g <- global) {
      nonFatalCatch {
        g.askShutdown()
        g.close()
      }
    }
    global.restart() // more of a "reset", as nothing's eagerly started
  }

  private def path(input: Input): String = input match {
    case Input.File(path, _) => path.toString()
    case Input.VirtualFile(path, _) => path
    case Input.Slice(i, _, _) => path(i)
    case _ => input.getClass.getSimpleName
  }
}
