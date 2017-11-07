package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorAbstractConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 * // Not supported.
 */
public class Z3BitVectorComparableConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements ComparableConstraintEncoder<BoolExpr> {

    public Z3BitVectorComparableConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, solver, z3BitVectorFormatTranslator);
    }

    @Override
    public BoolExpr encodeVariable_Variable(VariableSlot fst, VariableSlot snd) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeVariable_Constant(VariableSlot fst, ConstantSlot snd) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeConstant_Variable(ConstantSlot fst, VariableSlot snd) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd) {
        return defaultEncoding();
    }
}
