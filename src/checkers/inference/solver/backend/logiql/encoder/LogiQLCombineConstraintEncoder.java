package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Created by mier on 07/11/17.
 */
public class LogiQLCombineConstraintEncoder extends LogiQLAbstractConstraintEncoder implements CombineConstraintEncoder<String> {

    public LogiQLCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
    }

    @Override
    public String encodeVariable_Variable(VariableSlot target, VariableSlot declared, VariableSlot result) {
        return defaultEncoding();
    }

    @Override
    public String encodeVariable_Constant(VariableSlot target, ConstantSlot declared, VariableSlot result) {
        return defaultEncoding();
    }

    @Override
    public String encodeConstant_Variable(ConstantSlot target, VariableSlot declared, VariableSlot result) {
        return defaultEncoding();
    }

    @Override
    public String encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, VariableSlot result) {
        return defaultEncoding();
    }
}
