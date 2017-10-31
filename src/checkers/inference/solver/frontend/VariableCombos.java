package checkers.inference.solver.frontend;

import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * VariableCombos takes slots for a given constraint, and down cast them
 * to corresponding VariableSlot and ConstantSlot, then passing them to
 * corresponding methods in this class.
 *
 * It is used in Serializer, and defines a finer process of different
 * compositions of slots for a given constraint.
 *
 * The real serialization logic is implemented within the methods in
 * this class.
 * 
 * @author jianchu
 *
 * @param <T> Type of constraint.
 * @param <S> Encoding type for constraint.
 */
public abstract class VariableCombos<T extends Constraint, S> {

    private final S emptyValue;

    public VariableCombos(S emptyValue) {
        this.emptyValue = emptyValue;
    }

    protected S variable_variable(VariableSlot slot1, VariableSlot slot2, T constraint) {
        return defaultAction();
    }

    protected S constant_variable(ConstantSlot slot1, VariableSlot slot2, T constraint) {
        return defaultAction();
    }

    protected S variable_constant(VariableSlot slot1, ConstantSlot slot2, T constraint) {
        return defaultAction();
    }

    protected S constant_constant(ConstantSlot slot1, ConstantSlot slot2, T constraint) {
        return defaultAction();
    }

    protected S variable_variable(VariableSlot target, VariableSlot decl, VariableSlot result,
            CombineConstraint constraint) {
        return defaultAction();
    }

    protected S constant_variable(ConstantSlot target, VariableSlot decl, VariableSlot result,
            CombineConstraint constraint) {
        return defaultAction();
    }

    protected S variable_constant(VariableSlot target, ConstantSlot decl, VariableSlot result,
            CombineConstraint constraint) {
        return defaultAction();
    }

    protected S constant_constant(ConstantSlot target, ConstantSlot decl, VariableSlot result,
            CombineConstraint constraint) {
        return defaultAction();
    }

    public S defaultAction(Slot slot1, Slot slot2, T constraint) {
        return emptyValue;
    }

    public S defaultAction() {
        return emptyValue;
    }

    public S accept(Slot slot1, Slot slot2, T constraint) {

        final S result;

        if (slot1 instanceof ConstantSlot) {
            if (slot2 instanceof ConstantSlot) {
                result = constant_constant((ConstantSlot) slot1, (ConstantSlot) slot2, constraint);
            } else {
                result = constant_variable((ConstantSlot) slot1, (VariableSlot) slot2, constraint);
            }
        } else if (slot2 instanceof ConstantSlot) {
            result = variable_constant((VariableSlot) slot1, (ConstantSlot) slot2, constraint);
        } else {
            result = variable_variable((VariableSlot) slot1, (VariableSlot) slot2, constraint);
        }
        return result;
    }

    public S accept(Slot target, Slot decl, Slot result, T constraint) {

        final S combineResult;

        if (constraint instanceof CombineConstraint && result instanceof VariableSlot) {
            VariableSlot vResult = (VariableSlot) result;
            CombineConstraint comConstraint = (CombineConstraint) constraint;
            if (target instanceof ConstantSlot) {
                if (decl instanceof ConstantSlot) {
                    combineResult = constant_constant((ConstantSlot) target, (ConstantSlot) decl,
                            vResult, comConstraint);
                } else {
                    combineResult = constant_variable((ConstantSlot) target, (VariableSlot) decl,
                            vResult, comConstraint);
                }
            } else if (decl instanceof ConstantSlot) {
                combineResult = variable_constant((VariableSlot) target, (ConstantSlot) decl, vResult,
                        comConstraint);
            } else {
                combineResult = variable_variable((VariableSlot) target, (VariableSlot) decl, vResult,
                        comConstraint);
            }
            return combineResult;
        } else {
            return defaultAction();
        }
    }
}
