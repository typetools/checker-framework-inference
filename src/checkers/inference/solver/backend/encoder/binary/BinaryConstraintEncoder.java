package checkers.inference.solver.backend.encoder.binary;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Created by mier on 06/11/17.
 */
public interface BinaryConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot fst, ConstantSlot snd);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot fst, VariableSlot snd);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd);
}
