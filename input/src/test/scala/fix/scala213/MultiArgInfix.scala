/*
rule = fix.scala213.MultiArgInfix
*/
package fix.scala213

object MultiArgInfix {
  trait PipeToSupport {
    def to(recipient: Int, sender: Int): Unit
    def foo[A](a: A, i: String): Unit
  }

  def p: PipeToSupport = ???

  p to (1, 2)
  p /* c1 */ to (3, 4 + 5)

  p foo (1, "2")
  p /* c2 */ foo[Long]  /* c3 */ (3, "" + 4)
}
