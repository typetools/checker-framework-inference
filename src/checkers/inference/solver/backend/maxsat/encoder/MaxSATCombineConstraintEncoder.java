package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * Created by mier on 07/11/17.
 */
public class MaxSATCombineConstraintEncoder extends MaxSATAbstractConstraintEncoder implements CombineConstraintEncoder<VecInt[]> {

    public MaxSATCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, typeToInt);
    }

    @Override
    public VecInt[] encodeVariable_Variable(VariableSlot target, VariableSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public VecInt[] encodeVariable_Constant(VariableSlot target, ConstantSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public VecInt[] encodeConstant_Variable(ConstantSlot target, VariableSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public VecInt[] encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, Slot result) {
        return defaultEncoding();
    }
}
