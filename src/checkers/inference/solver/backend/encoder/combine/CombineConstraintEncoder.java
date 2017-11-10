package checkers.inference.solver.backend.encoder.combine;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Combine constraints encoder. All concrete encoder that supports encoding CombineConstraint should implement this
 * interface. It has have four encodeXXX() methods because depending on the combination of target and declared
 * slots in combine constraint, different encodeXXX() method should be used to allow more efficient encoding. Result
 * slot is always a CombVariableSlot since it is always generated instead of pre-existed in the source code. So result
 * slot doesn't affect dispatching encodeXXX() method.
 */
public interface CombineConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot target, VariableSlot declared, VariableSlot result);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot target, ConstantSlot declared, VariableSlot result);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot target, VariableSlot declared, VariableSlot result);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, VariableSlot result);
}
