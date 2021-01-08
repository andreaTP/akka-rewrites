/*
rule = fix.ExplicitBangImport
*/
package fix

import akka.actor.Actor

class Something {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

}
