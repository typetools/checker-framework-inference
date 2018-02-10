package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

/**
 * Abstract base class for every Z3BitVector constraint encoders.
 */
public class Z3BitVectorAbstractConstraintEncoder extends AbstractConstraintEncoder<BoolExpr> {

    protected final Context context;
    protected final Z3BitVectorFormatTranslator z3BitVectorFormatTranslator;

    public Z3BitVectorAbstractConstraintEncoder(Lattice lattice, Context context,
            Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, context.mkTrue(), context.mkFalse());
        this.context = context;
        this.z3BitVectorFormatTranslator = z3BitVectorFormatTranslator;
    }
}
