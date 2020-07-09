package fix.scala213.nullaryOverride

import scala.reflect.ClassTag

object ImplicitAsNullary {
  // bug found when rewrite akka
  trait CommandResult[Command, Event, State] {
    def eventOfType[E <: Event : ClassTag]: E
  }
  class CommandResultImpl[Command, Event, State, Reply] extends CommandResult[Command, Event, State] {
    // must not add `()`
    override def eventOfType[E <: Event: ClassTag]: E = ???
  }
}
