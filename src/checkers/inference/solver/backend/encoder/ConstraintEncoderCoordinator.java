package checkers.inference.solver.backend.encoder;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;

/**
 * A coordinator class that has the logic how each encoder encodes its supported constraint
 */
public class ConstraintEncoderCoordinator {

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
                return encoder.encodeVariable_Variable(
                        (VariableSlot) constraint.getTarget(), (VariableSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant(
                        (VariableSlot) constraint.getTarget(), (ConstantSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable(
                        (ConstantSlot) constraint.getTarget(), (VariableSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
            case CONSTANT_CONSTANT:
                return encoder.encodeConstant_Constant(
                        (ConstantSlot) constraint.getTarget(), (ConstantSlot) constraint.getDeclared(), (VariableSlot) constraint.getResult());
        }
        return null;
    }

    public static <ConstraintEncodingT> ConstraintEncodingT redirect(PreferenceConstraint constraint, PreferenceConstraintEncoder<ConstraintEncodingT> encoder) {
        return encoder.encode(constraint);
    }

    public static <ConstraintEncodingT> ConstraintEncodingT redirect(ExistentialConstraint constraint, ExistentialConstraintEncoder<ConstraintEncodingT> encoder) {
        return encoder.encode(constraint);
    }
}
