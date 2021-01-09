/*
rule = fix.ExplicitBangImport
*/
package fix

import akka.actor._

class WildcardImport {

  val sender = new Actor()

  def foo() = {
    sender ! "hello"
  }

}
