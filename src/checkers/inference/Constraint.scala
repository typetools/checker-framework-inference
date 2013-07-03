package checkers.inference
import com.sun.source.tree.Tree


/**
 * Note:  When this class was originally created, we intended to NEVER need the Annotated Type Mirror
 * because as the InferenceTreeVisitor descended into the tree it would create all needed constraints
 * between all of the relevant slots.  However, there is now a class of constraints (created for the
 * Verigames project) that requires more information than just the the variable that corresponds to the
 * "main annotation" of a type.  For these variables we include the AnnotatedTypeMirror instead of
 * a Slot, so that we can extract all Slots that are in that AnnotatedTypeMirror.  These two classes of
 * Constraints are outlined below and separated by comments
 */

sealed abstract trait Constraint

// Constraints without AnnotatedTypeMirrors

case class SubtypeConstraint(sub: Slot, sup: Slot) extends Constraint {
  override def toString(): String = {
    "subtype constraint: " + sub + "  <:  " + sup
  }
}

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


// Constraints with AnnotatedTypeMirrors

//TODO JB: For each typeArgs and args argument we can have multiple Slots due to type parameterization/arrays
case class CallInstanceMethodConstraint(callervp: VariablePosition, receiver: Slot, calledmeth: CalledMethodPos,
    typeargs: List[Slot], args: List[Slot], result: List[Slot]) extends Constraint {
  override def toString(): String = {
    "call instance method constraint; caller " + callervp + "; receiver slot: " + receiver + "; called method: " + calledmeth +
    (if (typeargs!=null && !typeargs.isEmpty) { "<" + typeargs + ">" }
     else { "" } ) +
    (if (args!=null && !args.isEmpty) { "(" + args + ") " }
     else { "() " }) +
    "result: " + result
  }
}

// Needs the fieldslot and fieldvp, because the slot wouldn't identify a pre-annotated field.
case class FieldAccessConstraint(context: VariablePosition, receiver: Slot, fieldslot: Slot, fieldvp: FieldVP,
                                 secondaryVariables : List[Slot]) extends Constraint {
  override def toString(): String = {
    "field access constraint; context " + context + "; receiver slot: " + receiver + "; field: " + fieldslot + " " +
    "pos: " + fieldvp  + " secondaryVariables: [ " + secondaryVariables.mkString(", ")  + " ]"
  }
}

case class FieldAssignmentConstraint(context: VariablePosition, recv: Slot, field: Slot, right: Slot) extends Constraint {
  override def toString(): String = {
    "assignment constraint; context " + context + "; receiver slot: " + recv + " field: " + field + "; right slot: " + right
  }
}

// TODO: handle local variables
case class AssignmentConstraint(context: VariablePosition, left: Slot, right: Slot) extends Constraint {
  override def toString(): String = {
    "assignment constraint; context " + context + "; left slot: " + left + "; right slot: " + right
  }
}
