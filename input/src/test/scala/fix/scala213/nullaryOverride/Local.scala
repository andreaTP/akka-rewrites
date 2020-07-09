/*
rule = fix.scala213.NullaryOverride
*/
package fix.scala213.nullaryOverride

object Local {
  trait Actor { // bug found when rewrite akka
    def postStop(): Unit = ()
    def prop: Unit
  }

  def local() = {
    new Actor { // rewrite
      override def postStop: Unit = ???
      def prop() = ???
    }

    new Actor { // keep
      override def postStop(): Unit = ???
      def prop = ???
    }
  }
}
