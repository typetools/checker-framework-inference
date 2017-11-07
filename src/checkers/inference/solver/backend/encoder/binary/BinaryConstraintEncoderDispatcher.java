package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.SlotSlotCombo;

/**
 * Created by mier on 07/11/17.
 */
public class BinaryConstraintEncoderDispatcher {

    public static <ConstraintEncodingT> ConstraintEncodingT dispatch(
            BinaryConstraint constraint, BinaryConstraintEncoder<ConstraintEncodingT> encoder) {
        SlotSlotCombo combo = SlotSlotCombo.valueOf(constraint);
        switch (combo) {
            case VARIABLE_VARIABLE:
                return encoder.encodeVariable_Variable((VariableSlot) constraint.getFirst(), (VariableSlot) constraint.getSecond());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant((VariableSlot) constraint.getFirst(), (ConstantSlot) constraint.getSecond());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable((ConstantSlot) constraint.getFirst(), (VariableSlot) constraint.getSecond());
            case CONSTANT_CONSTANT:
                return encoder.encodeConstant_Constant((ConstantSlot) constraint.getFirst(), (ConstantSlot) constraint.getSecond());
        }
        return null;
    }
}
