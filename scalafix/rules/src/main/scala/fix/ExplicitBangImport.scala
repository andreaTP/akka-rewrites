package fix

import scalafix.v1._

import scala.meta._

class ExplicitBangImport extends SyntacticRule("fix.ExplicitBangImport") {

  def findImportee(importees: List[scala.meta.Importee]): Boolean = {
    importees.exists { _ match {
      case importee"actorRef2Scala" => true
      case x => false
    }}
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree
      .collect {
        case source: Source =>
          val skipImport = source.collect {
            case tree: Tree =>
              val inAkkaActorPackage = !tree.collect {
                case _@ Pkg(ref, _) if ref.toString == "akka.actor" =>
                  true
              }.isEmpty


             lazy val alreadyImported = tree.collect {
               case _@importer"akka.actor._" =>
                 true
               case _@importer"akka.actor.{..$importees}" if findImportee(importees) =>
                 true
             }.exists(_ == true)

            lazy val containsDefinition =
              !tree.collect {
                case _@ Defn.Def(_, Term.Name("actorRef2Scala"), _, _, _, _) =>
                  true
              }.isEmpty

              (inAkkaActorPackage || alreadyImported || containsDefinition)
          }.exists(_ == true)

          lazy val hasBang =
            source.collect {
              case _ @ Term.ApplyInfix(_, Term.Name("!"), _, _) =>
                true
            }.nonEmpty

          lazy val patch = source.collect {
            case t@importer"akka.actor.{..$importees}" if importees.size > 1 =>
              Patch.replaceTree(t, t.toString().replace("}", ",actorRef2Scala}"))
          }

          if (!skipImport && hasBang) {
            patch.headOption.getOrElse {
              Patch.addGlobalImport(importer"akka.actor.actorRef2Scala")
            }
          } else {
            Patch.empty
          }
      }
      .asPatch
  }
}
