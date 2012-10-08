package checkers.inference

import scala.tools.cmd._

/**
 * Main entry point for the Tunable Type Inference project.
 *
 * First take advantage of the meta-options:
 *
 *    // this command creates an executable runner script "TTIRun"
 *    % scala checkers.inference.TTIRun --self-update TTIRun
 *
 *    // this one creates and sources a completion file - note backticks
 *    % `./TTIRun --bash`
 *
 *    // and now you have a runner with working completion
 *    % ./TTIRun --<tab>
 *       --action           --defint           --int
 *       --bash             --defstr           --str
 *       --defenv           --self-update      --unary
 *
 * The normal option configuration is possibly self-explanatory.
 */
trait TTIRunSpec extends Spec with Meta.StdOpts with Interpolation {
  lazy val referenceSpec = TTIRunSpec
  lazy val programInfo = Spec.Info("TTIRun", "Usage: TTIRun [<options>]", "checkers.inference.TTIRun")

  help("""Usage: TTIRun [<options>]""")

  heading("Flags:")

  // TODO: these are specific to GUT; change into generic option for all checkers, like -Alint?
  val optEnforceOaM = "OaM" / "enable Owner-as-Modifier" --?
  // val optUseWeights = "Weighted" / "enable weights" --?
  // TODO: how can I set the default for a unary option?
  val optNoWeights = "unweighted" / "disable weights" --?

  heading("Checker configuration:")
  val optChecker = "checker" / "the Checker instance to use" defaultTo "GUTI.GUTIChecker"
  val optVisitor = "visitor" / "the Visitor instance to use" defaultTo "GUTI.GUTIVisitor"
  val optJaifFileName = "jaiffile" / "the JAIF file name" defaultTo "inference.jaif"

  heading("Solver configuration:")
  val optSolver = "solver" / "the ConstraintSolver to use" defaultTo "GUTI.GUTIConstraintSolver"
  val optWeightManager = "weightmgr" / "the WeightManager to use" defaultTo ""//"GUTI.GUTIWeightManager"

  // The solver is called with the CNF File as only argument
  val optWCNFSolver = "wcnfSolver" / "the WCNF solver to use" defaultTo
    "/homes/gws/wmdietl/research/tools/PBS-Solvers/sat4j.org/sat4j-maxsat.sh"
  val optWCNFFile = "wcnfFile" / "the WCNF file name" defaultTo "/tmp/test1.wcnf"

  heading("Expansions:")
  /* TODO: add a separate checker for testing
  val optExpandTest = "test" / "use test config" expandTo
    ("--checker", "checkers.inference.TestInferenceChecker",
      "--visitor", "checkers.inference.TestInferenceVisitor",
      "--solver", "",
      "--weightmgr", "") */
  val optExpandGUT = "gut" / "use GUT config" expandTo
    ("--checker", "GUTI.GUTIChecker",
      "--visitor", "GUTI.GUTIVisitor",
      "--solver", "GUTI.GUTIConstraintSolver",
      "--weightmgr", ""/*, "GUTI.GUTIWeightManager"*/)
  val optExpandEnerJ = "enerj" / "use EnerJ config" expandTo
    ("--checker", "enerji.PrecisionIChecker",
      "--visitor", "enerji.PrecisionIVisitor",
      "--solver", "enerji.PrecisionConstraintSolver",
      "--weightmgr", "")

  heading("Debugging:")
  val optDebug = "debug" / "enable debugging all classes" --?
  val optVersion = "version" / "output version information" --?
  val optDebugClass = "debugclass" / "enable debugging a particular class" --|
}

object TTIRunSpec extends TTIRunSpec with Property {
  lazy val propMapper = new PropertyMapper(TTIRunSpec)

  type ThisCommandLine = SpecCommandLine
  def creator(args: List[String]) =
    new SpecCommandLine(args) {
      override def errorFn(msg: String) = { println("Error: " + msg); System.exit(0) }
    }
}

class TTIRun(args: List[String]) extends {
  val parsed = TTIRunSpec(args: _*)
} with TTIRunSpec with Instance {
  import java.lang.reflect._

  def helpMsg = TTIRunSpec.helpMsg
  def ttiRunSpecMethods = this.getClass.getMethods.toList
  private def isTTIRun(m: Method) = (m.getName startsWith "opt") && !(m.getName contains "$") && (m.getParameterTypes.isEmpty)

  def ttiRunString(ms: List[Method]) = {
    val longest = ms map (_.getName.length) max
    val formatStr = "    %-" + longest + "s: %s"
    val xs = ms map (m => formatStr.format(m.getName, m.invoke(this)))

    "TTIRun(\n  " +
      (xs mkString "\n  ") +
      "\n" +
      "      Residual args: " +
      (this.residualArgs mkString ", ") +
      "\n)\n"
  }

  override def toString = ttiRunString(ttiRunSpecMethods filter isTTIRun)
}

object TTIRun {
  def main(args: Array[String]): Unit = {
    val runner = new TTIRun(args.toList)

    if (args.isEmpty) {
      println(runner.helpMsg)
    } else {
      // println(runner)
      InferenceMain.run(runner)
    }
  }
}