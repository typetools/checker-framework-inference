package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 */
public class Z3BitVectorPreferenceConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements PreferenceConstraintEncoder<BoolExpr> {

    public Z3BitVectorPreferenceConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, solver, z3BitVectorFormatTranslator);
    }

    /**
     * Return an equality constriant between variable and constant goal.
     * The caller should add the serialized constraint with soft option.
     */
    @Override
    public BoolExpr encode(PreferenceConstraint constraint) {
        VariableSlot variableSlot = constraint.getVariable();
        ConstantSlot constantSlot = constraint.getGoal();

        BitVecExpr varBV = z3BitVectorFormatTranslator.serializeVarSlot(variableSlot);
        BitVecExpr constBV = z3BitVectorFormatTranslator.serializeConstantSlot(constantSlot);

        return context.mkEq(varBV, constBV);
    }
}
