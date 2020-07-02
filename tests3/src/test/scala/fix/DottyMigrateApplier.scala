package fix

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.meta._
import scalafix.testkit.{RuleTest, SemanticRuleSuite}

class DottyMigrateApplier extends SemanticRuleSuite {
  runAllTests()

  override def evaluateTestBody(diffTest: RuleTest): Unit = {
    val (rule, sdoc) = diffTest.run.apply()
    rule.beforeStart()

    val (fixed, _) =
      try rule.semanticPatch(sdoc, suppress = false)
      finally rule.afterComplete()
    val tokens = fixed.tokenize.get
    val obtained = SemanticRuleSuite.stripTestkitComments(tokens)

    val output = props.outputSourceDirectories.head.resolve(diffTest.path.testPath).toNIO

    Files.createDirectories(output.getParent)
    Files.write(output, obtained.getBytes(UTF_8))
  }
}
