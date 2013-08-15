package checkers.inference
import com.sun.source.tree.Tree
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror.{AnnotatedExecutableType, AnnotatedTypeVariable, AnnotatedDeclaredType}


/**
 * Constraints represent relationships between variables in a Java program.  In general, a constraint is
 * generated for every location that would normally cause a "check" in the Checker Framework
 * e.g.
 *   MyClass<T1> a = ...
 *   MyClass<T2> b = ...
 *   a = b;
 *
 *   This code would normally cause a commonAssignmentCheck which would ensure that b is a subtype of a.
 *   In the inference framework we would instead generate a SubtypeConstraint( b, a ) and EqualityConstraint( T1, T2 )
 *
 * Note: In the example constraints above the values a, b, T1, and T2 would really be replaced by @VarAnnot ids
 * for their respective variable locations.
 *
 * Note:  When this class was originally created, we intended to NEVER need the Annotated Type Mirror
 * because as the InferenceTreeVisitor descended into the tree it would create all needed constraints
 * between all of the relevant slots.  However, there is now a class of constraints (created for the
 * Verigames project) that requires more information than just the the variable that corresponds to the
 * "main annotation" of a type.  For these variables we include the AnnotatedTypeMirror instead of
 * a Slot, so that we can extract all Slots that are in that AnnotatedTypeMirror.  These two classes of
 * Constraints are outlined below and separated by comments
 */

/**
 * Helper methods for Constraint instances
 */
object Constraint {
  def indent( numIndents : Int = 1) = {
    val indentStr = "    "
    val stringBuilder = new StringBuilder()
    for( i <- 0 until numIndents ) {
      stringBuilder ++= indentStr
    }
    stringBuilder.toString
  }

  def wrap( title : String, value : Object ) = title + ": " + value + "; "

  def wrapList[T]( title : Option[String], wrapper : (String, String), delimiter : String, list : List[T]) : String = {
     title.map( _ + ": " ).getOrElse("") +
     wrapper._1 +
       ( if(list == null) "null" else list.mkString( delimiter ) ) +
     wrapper._2
  }
}

import Constraint._

/**
 * Top level interface for Constraints
 */
sealed abstract trait Constraint

// Constraints without AnnotatedTypeMirrors

case class SubtypeConstraint(sub: Slot, sup: Slot) extends Constraint {
  override def toString(): String = {
    "subtype constraint: " + sub + "  <:  " + sup
  }
}

/** Represents viewpoint adaptation. */
case class CombineConstraint(target: Slot, decl: Slot, res: Slot) extends Constraint {
  override def toString(): String = {
    "combine constraint: " + target + "  |>  " + decl + "  =  " + res
  }
}

case class EqualityConstraint(ell: Slot, elr: Slot) extends Constraint {
  override def toString(): String = {
    "equality constraint: " + ell + " = " + elr
  }
}

case class InequalityConstraint(context: VariablePosition, ell: Slot, elr: Slot) extends Constraint {
  override def toString(): String = {
    "inequality constraint at " + context + ": " + ell + " != " + elr
  }
}

case class ComparableConstraint(ell: Slot, elr: Slot) extends Constraint {
  override def toString(): String = {
    "comparable constraint: " + ell + " <:> " + elr
  }
}

// NOTE: At the time of writing this comment, Verigames (CodeJam) was the only project that
// used the constraints below

abstract class SubboardCallConstraint[CALLED_VP <: VariablePosition](
  val contextVp : VariablePosition,
  val calledVp  : CALLED_VP,
  val receiver  : Slot,
  val methodTypeParamLBs : List[Slot],
  val classTypeParamLBs  : List[Slot],

  val methodTypeArgs : List[Slot],
  val classTypeArgs  : List[Slot],
  val args           : List[Slot],
  val result         : List[Slot],


  val slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
  val equivalentSlots : Set[(Slot, Slot)]
)  extends Constraint {

  protected def fieldsToString() = {
    List[(String,Object)](
      "contextVp"        ->  contextVp,
      "calledVp"         ->  calledVp,
      "receiver"         ->  receiver,
      "methodTypeParamLBs" -> ( "< " + methodTypeParamLBs.mkString(", ") + " >" ),
      "classTypeParamLBs"  -> ( "< " + classTypeParamLBs.mkString(", ")  + " >" ),

      "methodTypeArgs" -> ( "< " + methodTypeArgs.mkString(", ") + " >" ),
      "classTypeArgs"  -> ( "< " + classTypeArgs.mkString(", ")  + " >" ),

      "args"           -> ( "( " + args.mkString(", ") + " )" ),
      "result"         -> result
    ).map( nameToValue => wrap( nameToValue._1, nameToValue._2 ) ).mkString("\n")
  }

  override def toString() = {
    this.getClass().getName + "(\n" + fieldsToString() + "\n)"
  }
}

class FieldAccessConstraint(
  contextVp : VariablePosition,
  calledVp  : FieldVP,
  receiver  : Slot,

  classTypeParamLBs  : List[Slot],
  classTypeArgs      : List[Slot],

  field            : List[Slot],

  slotToBounds     : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots  : Set[(Slot, Slot)]

) extends SubboardCallConstraint[FieldVP]( contextVp, calledVp, receiver, List.empty[Slot], classTypeParamLBs,
                      List.empty[Slot], classTypeArgs, List.empty[Slot], field, slotToBounds, equivalentSlots )

class FieldAssignmentConstraint(
  contextVp : VariablePosition,
  calledVp  : FieldVP,
  receiver  : Slot,

  classTypeParamLBs  : List[Slot],
  classTypeArgs      : List[Slot],

  field            : List[Slot],
  rhs              : List[Slot],

  slotToBounds     : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots  : Set[(Slot, Slot)]

 ) extends SubboardCallConstraint[FieldVP]( contextVp, calledVp, receiver, List.empty[Slot], classTypeParamLBs,
                    List.empty[Slot], classTypeArgs, rhs, List.empty[Slot], slotToBounds, equivalentSlots )

class InstanceMethodCallConstraint(
  val isConstructor : Boolean,
  contextVp : VariablePosition,
  calledVp  : CalledMethodPos,
  receiver  : Slot,
  methodTypeParamLBs : List[Slot],
  classTypeParamLBs  : List[Slot],

  methodTypeArgs : List[Slot],
  classTypeArgs  : List[Slot],
  args           : List[Slot],
  result         : List[Slot],


  slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots : Set[(Slot, Slot)]
) extends SubboardCallConstraint[CalledMethodPos]( contextVp, calledVp, receiver,
                                                   methodTypeParamLBs, classTypeParamLBs, methodTypeArgs, classTypeArgs,
                                                   args, result, slotToBounds, equivalentSlots )

class StaticMethodCallConstraint(contextVp : VariablePosition,
                                 calledVp  : CalledMethodPos,
                                 receiver  : Slot,
                                 methodTypeParamLBs : List[Slot],
                                 classTypeParamLBs  : List[Slot],

                                 methodTypeArgs : List[Slot],
                                 classTypeArgs  : List[Slot],
                                 args           : List[Slot],
                                 result         : List[Slot],


                                 slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
                                 equivalentSlots : Set[(Slot, Slot)]
) extends SubboardCallConstraint[CalledMethodPos]( contextVp, calledVp, receiver,
                                                   methodTypeParamLBs, classTypeParamLBs, methodTypeArgs, classTypeArgs,
                                                   args, result, slotToBounds, equivalentSlots )



// TODO: handle local variables
case class AssignmentConstraint(context: VariablePosition, left: Slot, right: Slot) extends Constraint {
  override def toString(): String = {
    "assignment constraint; context " + context + "; left slot: " + left + "; right slot: " + right
  }
}
