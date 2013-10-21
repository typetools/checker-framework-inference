package checkers.inference.util

import checkers.inference._
import javax.lang.model.element.AnnotationMirror
import java.io._
import checkers.inference.WeightInfo
import checkers.inference.Variable
import checkers.inference.RefinementVariable
import checkers.inference.CombVariable

class ConstraintConversionSolver extends ConstraintSolver {

  lazy val PrintConstraints = System.getProperty("PRINT", "false").equals("true")
  lazy val Directory = System.getProperty("OUTPUT", System.getProperty("user.dir"))

  var _timing = 0L

  override def solve(variables: List[Variable],
                     combvariables: List[CombVariable],
                     refinementVariables: List[RefinementVariable],
                     constraints: List[Constraint],
                     weights: List[WeightInfo],
                     params: TTIRun): Option[Map[AbstractVariable, AnnotationMirror]] = {

    val start = System.currentTimeMillis()
    val defaultQual = InferenceMain.slotMgr.extractConstant( InferenceMain.getRealChecker.defaultQualifier() )
    val convertedConstraints = SolverUtil.convertSubboardCalls( variables, constraints, defaultQual )
    val end = System.currentTimeMillis()

    printOriginals( System.out, constraints )
    printConverted( System.out, constraints )

    if( PrintConstraints ) {
      val originalsPw  = new PrintStream( new FileOutputStream( new File( Directory, "original.ccs" ) ) )
      val convertedPw = new PrintStream( new FileOutputStream( new File( Directory, "converted.css") ) )

      printOriginals( originalsPw, constraints          )
      printConverted( convertedPw, convertedConstraints )

      originalsPw.close
      convertedPw.close
    }

    _timing = end - start

    None
  }

  def printConstraints( out : PrintStream, constraints : List[Constraint]) {
    constraints.foreach( constraint =>  {
      out.println( "\n" + constraint.toString )
    })
    out.flush()
  }

  def printOriginals( out : PrintStream, originals : List[Constraint] ) {
    out.println("\n\nOriginal Constraints: ")
    printConstraints( out, originals)
  }

  def printConverted( out : PrintStream, originals : List[Constraint] ) {
    out.println("\n\nConverted Constraints: ")
    printConstraints( out, originals)
  }

  // Return the amount of time it took to convert the constraints
  def timing: String = _timing + " ms"

  // Return formatted version information
  def version: String = "ConstraintConversionSolver v1.0"
}
