package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;
import checkers.inference.solver.backend.encoder.ArithmeticConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.implication.ImplicationConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

/**
 * Z3 implementation of {@link checkers.inference.solver.backend.encoder.ConstraintEncoderFactory}.
 *
 * @see checkers.inference.solver.backend.encoder.ConstraintEncoderFactory
 */
public class Z3BitVectorConstraintEncoderFactory extends AbstractConstraintEncoderFactory<BoolExpr, Z3BitVectorFormatTranslator>{

    protected final Context context;

    public Z3BitVectorConstraintEncoderFactory(Lattice lattice, Context context,
            Z3BitVectorFormatTranslator formatTranslator) {
        super(lattice, formatTranslator);
        this.context = context;
    }

    @Override
    public Z3BitVectorSubtypeConstraintEncoder createSubtypeConstraintEncoder() {
        return new Z3BitVectorSubtypeConstraintEncoder(lattice, context, formatTranslator);
    }

    @Override
    public EqualityConstraintEncoder<BoolExpr> createEqualityConstraintEncoder() {
        return new Z3BitVectorEqualityConstraintEncoder(lattice, context, formatTranslator);
    }

    @Override
    public InequalityConstraintEncoder<BoolExpr> createInequalityConstraintEncoder() {
        // TODO InequalityEncoder can be supported.
        return null;
    }

    @Override
    public ComparableConstraintEncoder<BoolExpr> createComparableConstraintEncoder() {
        return null;
    }

    @Override
    public PreferenceConstraintEncoder<BoolExpr> createPreferenceConstraintEncoder() {
        return new Z3BitVectorPreferenceConstraintEncoder(lattice, context, formatTranslator);
    }

    @Override
    public CombineConstraintEncoder<BoolExpr> createCombineConstraintEncoder() {
        return null;
    }

    @Override
    public ExistentialConstraintEncoder<BoolExpr> createExistentialConstraintEncoder() {
        return null;
    }

    @Override
    public ImplicationConstraintEncoder<BoolExpr> createImplicationConstraintEncoder() {
        return null;
    }
  
    @Override
    public ArithmeticConstraintEncoder<BoolExpr> createArithmeticConstraintEncoder() {
        return null;
    }
}
