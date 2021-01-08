package fix

import akka.actor.Actor
import akka.actor.actorRef2Scala

class ExplicitBangImport {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

  def bar() = {
    if (!true) {
    } else {
    }
  }

}
