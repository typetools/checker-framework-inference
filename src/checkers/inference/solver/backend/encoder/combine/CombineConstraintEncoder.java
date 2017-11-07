package checkers.inference.solver.backend.encoder.combine;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * Created by mier on 07/11/17.
 */
public interface CombineConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encodeVariable_Variable(VariableSlot target, VariableSlot declared, Slot result);

    ConstraintEncodingT encodeVariable_Constant(VariableSlot target, ConstantSlot declared, Slot result);

    ConstraintEncodingT encodeConstant_Variable(ConstantSlot target, VariableSlot declared, Slot result);

    ConstraintEncodingT encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, Slot result);
}
