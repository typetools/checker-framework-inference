package checkers.inference.solver.backend.encoder;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;

/**
 * Created by mier on 08/11/17.
 */
public class ConstraintEncoderDispatcher {

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
