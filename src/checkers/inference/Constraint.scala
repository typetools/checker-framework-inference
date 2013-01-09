package checkers.inference
import com.sun.source.tree.Tree


sealed abstract trait Constraint

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

case class CallInstanceMethodConstraint(callervp: VariablePosition, receiver: Slot, calledmeth: CalledMethodPos,
    typeargs: List[Slot], args: List[Slot], result: Slot) extends Constraint {
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
case class FieldAccessConstraint(context: VariablePosition, receiver: Slot, fieldslot: Slot, fieldvp: FieldVP) extends Constraint {
  override def toString(): String = {
    "field access constraint; context " + context + "; receiver slot: " + receiver + "; field: " + fieldslot + " pos: " + fieldvp
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
