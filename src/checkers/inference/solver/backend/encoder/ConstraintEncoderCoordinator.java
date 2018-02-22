package checkers.inference.solver.backend.encoder;

import org.checkerframework.javacutil.ErrorReporter;
import checkers.inference.model.ArithmeticConstraint;
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
 * A coordinator class that has the coordinating logic how each encoder encodes its supported
 * constraint.
 * <p>
 * Dispatching example: this class dispatches the encoding of {@link BinaryConstraint} to the
 * corresponding encodeXXX_YYY() method in {@link BinaryConstraintEncoder} depending on the
 * {@link SlotSlotCombo} of {@link BinaryConstraint} that the encoder encodes.
 * <p>
 * Redirecting example: this class simply redirects encoding of {@link PreferenceConstraint} to
 * {@link PreferenceConstraintEncoder#encode(PreferenceConstraint)} method, as this kind of
 * constraint doesn't need the {@code SlotSlotCombo} information to encode it.
 *
 * @see BinaryConstraintEncoder
 * @see CombineConstraintEncoder
 * @see PreferenceConstraintEncoder
 * @see ExistentialConstraintEncoder
 */
public class ConstraintEncoderCoordinator {

    public static <ConstraintEncodingT> ConstraintEncodingT dispatch(BinaryConstraint constraint,
            BinaryConstraintEncoder<ConstraintEncodingT> encoder) {
        switch (SlotSlotCombo.valueOf(constraint.getFirst(), constraint.getSecond())) {
            case VARIABLE_VARIABLE:
                return encoder.encodeVariable_Variable((VariableSlot) constraint.getFirst(),
                        (VariableSlot) constraint.getSecond());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant((VariableSlot) constraint.getFirst(),
                        (ConstantSlot) constraint.getSecond());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable((ConstantSlot) constraint.getFirst(),
                        (VariableSlot) constraint.getSecond());
            case CONSTANT_CONSTANT:
                ErrorReporter.errorAbort("Attempting to encode a constant-constant combination "
                        + "for a binary constraint. This should be normalized to "
                        + "either AlwaysTrueConstraint or AlwaysFalseConstraint.");
                return null;
            default:
                ErrorReporter.errorAbort("Unsupported SlotSlotCombo enum.");
                return null;
        }
    }

    public static <ConstraintEncodingT> ConstraintEncodingT dispatch(CombineConstraint constraint,
            CombineConstraintEncoder<ConstraintEncodingT> encoder) {
        switch (SlotSlotCombo.valueOf(constraint.getTarget(), constraint.getDeclared())) {
            case VARIABLE_VARIABLE:
                return encoder.encodeVariable_Variable((VariableSlot) constraint.getTarget(),
                        (VariableSlot) constraint.getDeclared(),
                        (VariableSlot) constraint.getResult());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant((VariableSlot) constraint.getTarget(),
                        (ConstantSlot) constraint.getDeclared(),
                        (VariableSlot) constraint.getResult());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable((ConstantSlot) constraint.getTarget(),
                        (VariableSlot) constraint.getDeclared(),
                        (VariableSlot) constraint.getResult());
            case CONSTANT_CONSTANT:
                return encoder.encodeConstant_Constant((ConstantSlot) constraint.getTarget(),
                        (ConstantSlot) constraint.getDeclared(),
                        (VariableSlot) constraint.getResult());
            default:
                ErrorReporter.errorAbort("Unsupported SlotSlotCombo enum.");
                return null;
        }
    }

    public static <ConstraintEncodingT> ConstraintEncodingT dispatch(
            ArithmeticConstraint constraint,
            ArithmeticConstraintEncoder<ConstraintEncodingT> encoder) {
        switch (SlotSlotCombo.valueOf(constraint.getLeftOperand(), constraint.getRightOperand())) {
            case VARIABLE_VARIABLE:
                return encoder.encodeVariable_Variable(constraint.getOperation(),
                        (VariableSlot) constraint.getLeftOperand(),
                        (VariableSlot) constraint.getRightOperand(), constraint.getResult());
            case VARIABLE_CONSTANT:
                return encoder.encodeVariable_Constant(constraint.getOperation(),
                        (VariableSlot) constraint.getLeftOperand(),
                        (ConstantSlot) constraint.getRightOperand(), constraint.getResult());
            case CONSTANT_VARIABLE:
                return encoder.encodeConstant_Variable(constraint.getOperation(),
                        (ConstantSlot) constraint.getLeftOperand(),
                        (VariableSlot) constraint.getRightOperand(), constraint.getResult());
            case CONSTANT_CONSTANT:
                return encoder.encodeConstant_Constant(constraint.getOperation(),
                        (ConstantSlot) constraint.getLeftOperand(),
                        (ConstantSlot) constraint.getRightOperand(), constraint.getResult());
        }
        return null;
    }

    public static <ConstraintEncodingT> ConstraintEncodingT redirect(
            PreferenceConstraint constraint,
            PreferenceConstraintEncoder<ConstraintEncodingT> encoder) {
        return encoder.encode(constraint);
    }

    public static <ConstraintEncodingT> ConstraintEncodingT redirect(
            ExistentialConstraint constraint,
            ExistentialConstraintEncoder<ConstraintEncodingT> encoder) {
        return encoder.encode(constraint);
    }
}
