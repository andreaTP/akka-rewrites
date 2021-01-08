/*
*/
package akka

package object actor {

  implicit def actorRef2Scala(a: Actor) = new ActorRef()
}