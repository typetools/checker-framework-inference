package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 */
public class Z3BitVectorAbstractConstraintEncoder extends AbstractConstraintEncoder<BoolExpr> {

    public static BoolExpr EMPTY_BOOL_EXPR = null;
    // TODO Charles can help
    private static BoolExpr CONTRADICTORY_BOOL_EXPR = null;

    protected final Context context;
    protected final Optimize solver;
    protected final Z3BitVectorFormatTranslator z3BitVectorFormatTranslator;

    public Z3BitVectorAbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Context context,
                                                Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, EMPTY_BOOL_EXPR, CONTRADICTORY_BOOL_EXPR);
        this.context = context;
        this.solver = solver;
        this.z3BitVectorFormatTranslator = z3BitVectorFormatTranslator;
    }
}
