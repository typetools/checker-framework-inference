package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Binary constraints encoder. It has four encodeXXX() methods because depending
 * on the combination of two slots in binary constraint, different encodeXXX() method
 * should be used to allow more efficient encoding.
 */
public interface BinaryConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot fst, ConstantSlot snd);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd);
}
