package checkers.inference

case class WeightInfo(theVar: Variable, cons: Constant, weight: Int) {
}

abstract class WeightManager {
  def weight(variables: List[Variable], constraints: List[Constraint]): List[WeightInfo]
}