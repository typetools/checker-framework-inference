package checkers.inference

import javax.lang.model.element.AnnotationMirror
abstract class ConstraintSolver {

  def solve(variables: List[Variable],
    combvariables: List[CombVariable],
    refinementVariables : List[RefinementVariable],
    constraints: List[Constraint],
    weights: List[WeightInfo],
    params: TTIRun): Option[Map[AbstractVariable, AnnotationMirror]]

  // Return formatted timing information
  def timing: String

  // Return formatted version information
  def version: String
}
