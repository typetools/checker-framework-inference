package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 */
public class Z3BitVectorEqualityConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements EqualityConstraintEncoder<BoolExpr> {

    public Z3BitVectorEqualityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, solver, z3BitVectorFormatTranslator);
    }

    @Override
    public BoolExpr encodeVariable_Variable(VariableSlot fst, VariableSlot snd) {

        BitVecExpr varBv1 = z3BitVectorFormatTranslator.serializeVarSlot(fst);
        BitVecExpr varBv2 = z3BitVectorFormatTranslator.serializeVarSlot(snd);
        return context.mkEq(varBv1, varBv2);
    }

    @Override
    public BoolExpr encodeVariable_Constant(VariableSlot fst, ConstantSlot snd) {

        BitVecExpr constBv = z3BitVectorFormatTranslator.serializeConstantSlot(snd);
        BitVecExpr varBv = z3BitVectorFormatTranslator.serializeVarSlot(fst);
        return context.mkEq(constBv, varBv);
    }

    @Override
    public BoolExpr encodeConstant_Variable(ConstantSlot fst, VariableSlot snd) {

        BitVecExpr constBv = z3BitVectorFormatTranslator.serializeConstantSlot(fst);
        BitVecExpr varBv = z3BitVectorFormatTranslator.serializeVarSlot(snd);
        return context.mkEq(constBv, varBv);
    }

    @Override
    public BoolExpr encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd) {
        return verifier.areEqual(fst, snd) ? emptyValue : contradictoryValue;
    }
}
