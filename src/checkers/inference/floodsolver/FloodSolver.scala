package checkers.inference.floodsolver

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import checkers.basetype.BaseTypeChecker
import checkers.inference.AbstractLiteral
import checkers.inference.AbstractVariable
import checkers.inference.CombVariable
import checkers.inference.Constant
import checkers.inference.Constraint
import checkers.inference.ConstraintSolver
import checkers.inference.EqualityConstraint
import checkers.inference.InequalityConstraint
import checkers.inference.InferenceMain
import checkers.inference.LiteralNull
import checkers.inference.RefinementVariable
import checkers.inference.Slot
import checkers.inference.SubboardCallConstraint
import checkers.inference.SubtypeConstraint
import checkers.inference.TTIRun
import checkers.inference.Variable
import checkers.inference.WeightInfo
import javax.lang.model.element.AnnotationMirror
import checkers.inference.util.SolverUtil
import checkers.inference.SlotManager
import checkers.inference.SlotManager
import checkers.inference.StubBoardUseConstraint
import checkers.inference.BallSizeTestConstraint
import javacutils.AnnotationUtils
import SolverUtil.extractConstants
import scala.collection.mutable.HashSet
import java.util.Date


object FloodSolver {

    // startingSubtypes - List of known subtyped
  // constraints - If x then y style constraints
  // return - new list of known subtyped
  def floodSolve(startingSubtypes: List[Int], constraints: Map[Int, List[Int]]) : List[Int] = {
    val workingSet = new ListBuffer[Int]()
    workingSet ++= startingSubtypes
    val result = new HashSet[Int]()
    var i = 0
    println("Working set: " + workingSet)
    while (! workingSet.isEmpty) {
      i += 1
      if ( i % 1000 == 0) {
        println(new Date())
        println(i)
      }
      val newSubtypes = constraints.get(workingSet.head) match {
        case Some(list) => 
          list
        case _ => List[Int]()
      }
      result += workingSet.head
      workingSet.remove(0)
      workingSet ++= (newSubtypes.filter(!result.contains(_)))
    }

    result.toList
  }

  def main(args: Array[String]): Unit = {
    val res = floodSolve(List(1), Map(1 -> List(2), 2 -> List(3,4), 4 -> List(5), 6 -> List(7)))
    println(res)
  }
}

class FloodSolver extends ConstraintSolver {

  var top : AnnotationMirror = null
  var bot : AnnotationMirror = null

  def solve(variables: List[Variable],
    combvariables: List[CombVariable],
    refinementVariables : List[RefinementVariable],
    constraints: List[Constraint],
    weights: List[WeightInfo],
    params: TTIRun): Option[Map[AbstractVariable, AnnotationMirror]] = {
    
    val (forceTopVariables, knownSubtypes) = solveSubtypes(variables, combvariables, 
        refinementVariables, constraints, weights, params)
        
    if (knownSubtypes.intersect(forceTopVariables).size > 0) {
      None
    } else {
      Some(variables.map(avar => 
        if (knownSubtypes.contains(avar.id)) {
          (avar, bot)
        } else {
          (avar, top)
        }).toMap)
    }
  }
  
  def solveSubtypes(variables: List[Variable],
    combvariables: List[CombVariable],
    refinementVariables : List[RefinementVariable],
    constraints: List[Constraint],
    weights: List[WeightInfo],
    params: TTIRun): (List[Int], List[Int]) = {
    
    println("Starting Flood Solver =================")
    
    val qualHier = InferenceMain.getRealChecker.getTypeFactory().getQualifierHierarchy()
    // TODO: Only handles two qualifiers
    top = qualHier.getTopAnnotations().head
    bot = qualHier.getBottomAnnotations().head
    val convertedConstraints = simplifyStructuredConstraints(variables, constraints)
    val constraintMap = createConstraintMap(convertedConstraints, top, bot)
    val forceBottomVariables = constraintMap._1
    val forceTopVariables = constraintMap._2
    
    println("Total input constraints: " + constraints.size)
    println("Number of constraints after simplification: " + convertedConstraints.size)
    println("Number of force top: " + forceTopVariables.size)
    println("Number of force bottom: " + forceBottomVariables.size)
    println("Solving")
    
    val knownSubtypes = FloodSolver.floodSolve(forceBottomVariables, constraintMap._3)
    
    println("Inferered number of subtypes: " + knownSubtypes)
    (forceTopVariables, knownSubtypes)
  }
  
  def simplifyStructuredConstraints(variables: List[Variable], constraints: List[Constraint]): List[Constraint] = {
    // TODO: Subboard call constraints (remove filter)
     SolverUtil.convertSubboardCalls( variables, constraints, InferenceMain.slotMgr.extractSlot(top)).
        filterNot(_.isInstanceOf[StubBoardUseConstraint]).
        map(const => const match {
      case bs: BallSizeTestConstraint =>
        {throw new RuntimeException("Unhandled ball size test constraint")}
      case sb: SubboardCallConstraint[_] =>
        {throw new RuntimeException("Unhandled SubboardCallConstraint")}
      case c: Constraint => c
    })
  }

  def createConstraintMap(constraints: List[Constraint], top: AnnotationMirror, bot: AnnotationMirror) : (List[Int], List[Int], Map[Int, List[Int]]) = {
    val knownSub = new ListBuffer[Int]()
    val knownSuper = new ListBuffer[Int]()
    val subtypeMap = new HashMap[Int, List[Int]]()

    //TODO: Instead, we need to find the source of these and remove them
    val constraintsWithoutUnsatisfiables : Iterator[Constraint] = constraints.iterator.filter( SolverUtil.isSatisfiable _ )

    constraintsWithoutUnsatisfiables.foreach({
      case SubtypeConstraint(sub, sup) => {
        val (tsub, tsup) = (extractConstants(sub), extractConstants(sup))
        (tsub, tsup) match {
          // These are probably being generated by the subboard constraint converter
          case (`top`, `top`) =>
          case (`bot`, `bot`) =>
          case (`bot`, `top`) =>

          case (`top`, avar: AbstractVariable) => knownSuper.add(avar.id)
          case (avar: AbstractVariable, `top`) => {} // No Op
          case (`bot`, avar: AbstractVariable) => {} // No Op
          case (avar: AbstractVariable, `bot`) => knownSub.add(avar.id)
          case (rhs: AbstractVariable, lhs: AbstractVariable) => {
            // a = b
            // if a must be bottom, then rhs must be bottom
            val dependents = subtypeMap.getOrElse(lhs.id, List[Int]())
            subtypeMap += (lhs.id -> (rhs.id :: dependents))
          }
          case _ => throw new RuntimeException("Unhandled subtype constraints: " + sub + ", " + sup)
        }
      }
      case EqualityConstraint(sub, sup) => {
        val (tsub, tsup) = (extractConstants(sub), extractConstants(sup))
        (tsub, tsup) match {
          case (`top`, avar: AbstractVariable) => knownSuper.add(avar.id)
          case (avar: AbstractVariable, `top`) => knownSuper.add(avar.id)
          case (`bot`, avar: AbstractVariable) => knownSub.add(avar.id)
          case (avar: AbstractVariable, `bot`) => knownSub.add(avar.id)
          case (rhs: AbstractVariable, lhs: AbstractVariable) => {
            var dependents = subtypeMap.getOrElse(lhs.id, List[Int]())
            subtypeMap += (lhs.id -> (rhs.id :: dependents))

            dependents = subtypeMap.getOrElse(rhs.id, List[Int]())
            subtypeMap += (rhs.id -> (lhs.id :: dependents))
          }
          case _ => throw new RuntimeException("Unhandled equality constraints: " + sub + ", " + sup)
        }
      }
      case InequalityConstraint(varpos, sub, sup) => {
        val (tsub, tsup) = (extractConstants(sub), extractConstants(sup))
        (tsub, tsup) match {
          // We hold these truths to be self-evident
          case (`top`, `bot`) => {}
          case (`bot`, `top`) => {}

          case (`top`, avar: AbstractVariable) => knownSub.add(avar.id)
          case (avar: AbstractVariable, `top`) => knownSub.add(avar.id)
          case (`bot`, avar: AbstractVariable) => knownSuper.add(avar.id)
          case (avar: AbstractVariable, `bot`) => knownSuper.add(avar.id)
//          We cant encode this in the flood map
//          case (lhs: AbstractVariable, rhs: AbstractVariable) => {
//          }
          case _ => throw new RuntimeException("Unhandled inequality constraint: " + sub + ", " + sup)
        }
      }
      case const => {
        throw new RuntimeException("Unhandled constraint: " + const)
      }
    })
    return (knownSub.toList, knownSuper.toList, subtypeMap.toMap)
  }

  // Return formatted timing information
  def timing: String = ""

  // Return formatted version information
  def version: String = ""
}