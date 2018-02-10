package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Z3BitVectorEqualityConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements EqualityConstraintEncoder<BoolExpr> {

    public Z3BitVectorEqualityConstraintEncoder(Lattice lattice, Context context,
            Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, context, z3BitVectorFormatTranslator);
    }

    protected BoolExpr encode(Slot fst, Slot snd) {
        BitVecExpr varBv1 = fst.serialize(z3BitVectorFormatTranslator);
        BitVecExpr varBv2 = snd.serialize(z3BitVectorFormatTranslator);
        return context.mkEq(varBv1, varBv2);
    }

    @Override
    public BoolExpr encodeVariable_Variable(VariableSlot fst, VariableSlot snd) {
        return encode(fst, snd);
    }

    @Override
    public BoolExpr encodeVariable_Constant(VariableSlot fst, ConstantSlot snd) {
        return encode(fst, snd);
    }

    @Override
    public BoolExpr encodeConstant_Variable(ConstantSlot fst, VariableSlot snd) {
        return encode(fst, snd);
    }
}
