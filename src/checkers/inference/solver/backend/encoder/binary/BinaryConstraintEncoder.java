package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Interface that defines operations to encode a {@link checkers.inference.model.BinaryConstraint}. It has
 * four methods depending on the {@link checkers.inference.solver.backend.encoder.SlotSlotCombo} of the
 * two slots that constitute this binary constraint.
 *
 * @see checkers.inference.model.BinaryConstraint
 * @see checkers.inference.solver.backend.encoder.SlotSlotCombo
 */
public interface BinaryConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot fst, ConstantSlot snd);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd);
}
