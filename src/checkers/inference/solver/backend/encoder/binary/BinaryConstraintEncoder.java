package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Interface that defines operations to encode a {@link checkers.inference.model.BinaryConstraint}.
 * It has three methods depending on the
 * {@link checkers.inference.solver.backend.encoder.SlotSlotCombo} of the two slots that constitute
 * this binary constraint. Note that the constant-constant slot combination is normalized to either
 * True or False each BinaryConstraint's {@code create} method, thus will never need to be encoded.
 *
 * @see checkers.inference.model.BinaryConstraint
 * @see checkers.inference.solver.backend.encoder.SlotSlotCombo
 */
public interface BinaryConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot fst, ConstantSlot snd);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot fst, VariableSlot snd);
}
