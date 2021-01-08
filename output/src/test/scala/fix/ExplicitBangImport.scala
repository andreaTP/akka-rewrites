package fix

import akka.actor.Actor
import akka.actor.actorRef2Scala

class Something {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

}
