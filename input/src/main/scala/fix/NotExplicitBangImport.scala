/*
rule = fix.ExplicitBangImport
*/
package fix

class NotExplicitBangImport {

  def bar() = {
    if (!true) {
    } else {
    }
  }

}
