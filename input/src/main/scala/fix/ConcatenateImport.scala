/*
rule = fix.ExplicitBangImport
*/
package fix

import akka.actor.{ Actor, Some }

class ConcatenateImport {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

}
