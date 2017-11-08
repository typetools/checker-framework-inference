package checkers.inference.solver.backend.encoder.combine;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * Created by mier on 07/11/17.
 */
public interface CombineConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot target, VariableSlot declared, VariableSlot result);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot target, ConstantSlot declared, VariableSlot result);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot target, VariableSlot declared, VariableSlot result);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, VariableSlot result);
}
