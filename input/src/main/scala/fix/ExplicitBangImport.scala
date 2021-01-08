/*
rule = fix.ExplicitBangImport
*/
package fix

import akka.actor.Actor

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
