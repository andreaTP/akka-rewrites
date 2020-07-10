package fix.scala213

object MultiArgInfix {
  trait PipeToSupport {
    def to(recipient: Int, sender: Int): Unit
    def foo[A](a: A, i: String): Unit
  }

  def p: PipeToSupport = ???

  p.to(1, 2)
  p.to(3, 4 + 5)

  p.foo(1, "2")
  p.foo[Long](3, "" + 4)
}
