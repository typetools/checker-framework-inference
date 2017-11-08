package checkers.inference.solver.backend.encoder.combine;

import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.SlotSlotCombo;

/**
 * Created by mier on 07/11/17.
 */
public class CombineConstraintEncoderDispatcher {

    public static <ConstraintEncodingT> ConstraintEncodingT dispatch(
            CombineConstraint constraint, CombineConstraintEncoder<ConstraintEncodingT> encoder) {
        SlotSlotCombo combo = SlotSlotCombo.valueOf(constraint);
        switch (combo) {
            case VARIABLE_VARIABLE:
                return encoder.encodeVariable_Variable((VariableSlot) constraint.getTarget(), (VariableSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant((VariableSlot) constraint.getTarget(), (ConstantSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable((ConstantSlot) constraint.getTarget(), (VariableSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case CONSTANT_CONSTANT:
                return encoder.encodeConstant_Constant((ConstantSlot) constraint.getTarget(), (ConstantSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
        }
        return null;
    }
}
