package fix

import akka.actor.{ Actor, Some ,actorRef2Scala}

class ConcatenateImport {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

}
