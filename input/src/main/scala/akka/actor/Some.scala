/*
rule = fix.ExplicitBangImport
*/
package akka.actor

class Some {

  val actor = new Actor()

  val x = {
    actor ! "hello"
  }

}

