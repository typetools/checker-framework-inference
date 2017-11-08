package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLAbstractConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorAbstractConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 * // Not supported.
 */
public class Z3BitVectorCombineConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements CombineConstraintEncoder<BoolExpr> {

    public Z3BitVectorCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Context context,
                                               Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, context, solver, z3BitVectorFormatTranslator);
    }

    @Override
    public BoolExpr encodeVariable_Variable(VariableSlot target, VariableSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeVariable_Constant(VariableSlot target, ConstantSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeConstant_Variable(ConstantSlot target, VariableSlot declared, Slot result) {
        return defaultEncoding();
    }

    @Override
    public BoolExpr encodeConstant_Constant(ConstantSlot target, ConstantSlot declared, Slot result) {
        return defaultEncoding();
    }
}
