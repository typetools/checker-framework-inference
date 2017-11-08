package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ExistentialConstraint;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
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
public class Z3BitVectorExistentialConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements ExistentialConstraintEncoder<BoolExpr> {

    public Z3BitVectorExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Context context,
                                                   Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, context, solver, z3BitVectorFormatTranslator);
    }

    @Override
    public BoolExpr encode(ExistentialConstraint constraint) {
        return defaultEncoding();
    }
}
