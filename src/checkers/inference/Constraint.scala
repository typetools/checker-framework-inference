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

// NOTE: At the time of writing this comment, Verigames (FlowJam) was the only project that
// used the constraints below

/**
 * In FlowJam classic, we generate a "World", which is an entire program/library as well as an entire game.
 * A World is further separated into Levels (classes) and boards (methods).  Calls to methods, as
 * well as a few other types of statements, are represented by "subboard calls", i.e. a stand-in
 * representation of the method being called is shown in the current board (method) being played.
 * SubboardCallConstraint represents all the necessary data to add one of these subboard calls
 * to a board on which it is called.
 */
abstract class SubboardCallConstraint[CALLED_VP <: VariablePosition](
  /**
   * ContextVp is a variable position that identifies the method IN which this subboard call
   * occurred and therefore identifies the board in which it should be placed.
   */
  val contextVp : VariablePosition,

  /**
   * The CalledVp represents the position of the variablebeing called.  This field is generic
   * because both method calls as well as field accesses/assignments can generate subboard calls.
   */
  val calledVp : CALLED_VP,

  /**
   * The receiver representing the object on which a method was called or field was accessed/assigned to.
   * The receiver may be null (for constructors or static initializers).
   */
  val receiver : Slot,

  /**
   * methodTypeParamLBs represents the lower bounds for method type parameters in subboard calls representing
   * method invocations.  These should be used to add the subtype relationships above the board between a
   * method's type arguments and their bounds.
   */
  val methodTypeParamLBs : List[Slot],

  /**
   * classTypeParamLBs represents the lower bounds for cass type parameters in subboard calls that
   * represent non-static method invocations or field accesses/assignments.  These should be used to add the
   * subtype relationships above the board between class type arguments and their bounds.
   */
  val classTypeParamLBs : List[Slot],

  /**
   * methodTypeArgs represents the actual arguments to each type parameter for subboard calls representing
   * method invocations.  All variables of these arguments are extracted into lists and added to the
   * outer list of this variable.  I.e. this list consists of groups of variables each of which were extracted
   * from the same type argument.  There should be a one to one correspondence with the values in methodTypeParamLBs
   * And the first slot in each group should be bounded by the corresponding methodTypeParamLB
   */
  val methodTypeArgs : List[List[Slot]],


  /**
  * classTypeArgs represents the actual arguments on the receiver of the method invocation or field access/assignment
  * for a given subboard.  All variables of these arguments are extracted into lists and added to the
  * outer list of this variable.  I.e. this list consists of groups of variables each of which were extracted
  * from the same type argument.  There should be a one to one correspondence with the values in classTypeParamLBs
  * And the first slot in each group should be bounded by the corresponding methodTypeParamLB
  */
  val classTypeArgs : List[List[Slot]],

  /**
   * Represents inputs to this subboard call that are unrelated to generics.  In the case of method invocations
   * these would be the actual method arguments.
   */
  val args : List[Slot],

  /**
   * Represents an output value from this method call.  There is no corresponding input to values in result.
   * For method invocations this represents variables found on the return type.
   */
  val result : List[Slot],

  /**
   * Some slots found in all of the above fields are uses of a declared type parameter (i.e. type variables).
   * Each type variable is bounded by the slots on the lower bound of the declared parameter and the primary
   * annotation on the upper bound of that parameter.  This is a mapping between slots and their bounds.
   * Slots that are arguments to a type parameter should also be bounded by those bounds and should appear
   * in this map.  This map should then be used to apply the correct subtyping relationship above a subboard call.
   */
  val slotToBounds    : Map[Slot, Option[(Slot, Slot)]],

  /**
   * Equivalent slots are those that should have an equality constraint between them (and therefore be linked
   * in Verigames).  This should occur for each non-primary annotation for all arguments to a subboard
   * and the pipes within the called board that represent the corresponding parameters.
   */
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
  classTypeArgs      : List[List[Slot]],

  /**
   * The slots representing the field being accessed
   */
  field            : List[Slot],

  slotToBounds     : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots  : Set[(Slot, Slot)]

) extends SubboardCallConstraint[FieldVP]( contextVp, calledVp, receiver, List.empty[Slot], classTypeParamLBs,
                      List.empty[List[Slot]], classTypeArgs, List.empty[Slot], field, slotToBounds, equivalentSlots )

class FieldAssignmentConstraint(
  contextVp : VariablePosition,
  calledVp  : FieldVP,
  receiver  : Slot,

  classTypeParamLBs  : List[Slot],
  classTypeArgs      : List[List[Slot]],

  /**
   * The slots representing the field being accessed
   */
  field            : List[Slot],

  /**
   * The slots representing the value being assigned to the field.  Any non-primary
   * slots should be linked with the corresponding field slots.
   */
  rhs              : List[Slot],

  slotToBounds     : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots  : Set[(Slot, Slot)]

 ) extends SubboardCallConstraint[FieldVP]( contextVp, calledVp, receiver, List.empty[Slot], classTypeParamLBs,
                    List.empty[List[Slot]], classTypeArgs, rhs, List.empty[Slot], slotToBounds, equivalentSlots )

class InstanceMethodCallConstraint(
  val isConstructor : Boolean,
  contextVp : VariablePosition,
  calledVp  : CalledMethodPos,
  receiver  : Slot,
  methodTypeParamLBs : List[Slot],
  classTypeParamLBs  : List[Slot],

  methodTypeArgs : List[List[Slot]],
  classTypeArgs  : List[List[Slot]],
  args           : List[Slot],
  result         : List[Slot],


  slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
  equivalentSlots : Set[(Slot, Slot)]
) extends SubboardCallConstraint[CalledMethodPos]( contextVp, calledVp, receiver,
                                                   methodTypeParamLBs, classTypeParamLBs, methodTypeArgs, classTypeArgs,
                                                   args, result, slotToBounds, equivalentSlots )

class StaticMethodCallConstraint(contextVp : VariablePosition,
                                 calledVp  : CalledMethodPos,
                                 methodTypeParamLBs : List[Slot],
                                 methodTypeArgs : List[List[Slot]],
                                 args           : List[Slot],
                                 result         : List[Slot],
                                 slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
                                 equivalentSlots : Set[(Slot, Slot)]
) extends SubboardCallConstraint[CalledMethodPos]( contextVp, calledVp, null,
                                                   methodTypeParamLBs, List.empty[Slot], methodTypeArgs,
                                                   List.empty[List[Slot]], args, result, slotToBounds, equivalentSlots )



// TODO CON1: handle local variables
case class AssignmentConstraint(context: VariablePosition, left: Slot, right: Slot) extends Constraint {
  override def toString(): String = {
    "assignment constraint; context " + context + "; left slot: " + left + "; right slot: " + right
  }
}
