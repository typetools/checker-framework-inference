package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorAbstractConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 * // TODO Can be supported.
 */
public class Z3BitVectorInEqualityConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements InequalityConstraintEncoder<BoolExpr> {

    public Z3BitVectorInEqualityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Context context,
                                                  Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, context, solver, z3BitVectorFormatTranslator);
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
        return verifier.areInEqual(fst, snd) ? emptyValue : contradictoryValue;
    }
}
