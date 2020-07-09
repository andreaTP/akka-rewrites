package fix.scala213

import scala.annotation.unchecked.uncheckedVariance
import scala.reflect.ClassTag

trait ActorRef[-T] {
  def unsafeUpcast[U >: T @uncheckedVariance]: ActorRef[U]
}

trait ActorRefImpl[-T] extends ActorRef[T] {
  // must not add `()` to `unsafeUpcast()[U...`
  final override def unsafeUpcast[U >: T @uncheckedVariance]: ActorRef[U] = this.asInstanceOf[ActorRef[U]]
}

abstract class NullaryOverrideTest {
  class I1[T] extends Iterator[T] {
    def next(): T = ???
    def hasNext: Boolean = ???
    def foo(): T = ???
  }

  class I2[T] extends Iterator[T] {
    def next(): T = ???
    def hasNext: Boolean = ???
    def foo: T = ???
  }

  class I3 extends I1[Int] {
    override def hasNext: Boolean = ???
  }
}

class Override2 {
  class Meth { def m2p() = "" }
  trait Prop { def p2m: String }

  object meth2prop extends Meth {
    override def m2p() = "" // add `()`
  }
  object prop2meth extends Prop {
    def p2m = "" // remove `()`
  }

  this.meth2prop.m2p() // keep
  meth2prop.m2p()        // add `()`

  prop2meth.p2m // remove `()`
  prop2meth.p2m   // keep
}

object NullaryOverrideAkka {
  trait CommandResult[Command, Event, State] {
    def eventOfType[E <: Event : ClassTag]: E
  }
  class CommandResultImpl[Command, Event, State, Reply] extends CommandResult[Command, Event, State] {
    // must not add `()`
    override def eventOfType[E <: Event: ClassTag]: E = ???
  }
}
