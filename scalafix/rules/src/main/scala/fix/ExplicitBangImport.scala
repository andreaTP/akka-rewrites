package fix

import scalafix.v1._
import scala.meta._

class ExplicitBangImport extends SyntacticRule("fix.ExplicitBangImport") {

  def findImportee(importees: List[scala.meta.Importee]): Boolean = {
    importees.exists { _ match {
      case importee"actorRef2Scala" => true
      case _ => false
    }}
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree
      .collect {
        case source: Source if !source.toString().contains("package akka.actor") =>
          val addImport = source.collect {
            case tree: Tree =>
               val alreadyImported = tree.collect {
                 case _@importer"akka.actor._" => true
                 case _@importer"akka.actor.{..$importees}" =>
                   findImportee(importees)
               }

              lazy val hasBang =
                tree.collect {
                  case x @ Term.ApplyInfix(_, Term.Name("!"), _, _) =>
                    true
                }

              lazy val containsDefinition =
                tree.collect {
                  case _@ Defn.Def(_, Term.Name("actorRef2Scala"), _, _, _, _) =>
                    true
                }

              (!alreadyImported.exists(_ == true) && hasBang.nonEmpty && containsDefinition.isEmpty)
          }

          if (addImport.exists(_ == true)) {
            Patch.addGlobalImport(importer"akka.actor.actorRef2Scala")
          } else {
            Patch.empty
          }
      }
      .asPatch
  }
}
