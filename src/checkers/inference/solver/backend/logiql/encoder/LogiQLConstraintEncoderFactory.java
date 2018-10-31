package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;
import checkers.inference.solver.backend.encoder.ArithmeticConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.ComparableConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.EqualityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.implication.ImplicationConstraintEncoder;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.backend.logiql.LogiQLFormatTranslator;
import checkers.inference.solver.frontend.Lattice;

/**
 * LogiQL implementation of {@link checkers.inference.solver.backend.encoder.ConstraintEncoderFactory}.
 *
 * @see checkers.inference.solver.backend.encoder.ConstraintEncoderFactory
 */
public class LogiQLConstraintEncoderFactory extends AbstractConstraintEncoderFactory<String, LogiQLFormatTranslator> {

    public LogiQLConstraintEncoderFactory(Lattice lattice, LogiQLFormatTranslator formatTranslator) {
        super(lattice, formatTranslator);
    }

    @Override
    public SubtypeConstraintEncoder<String> createSubtypeConstraintEncoder() {
        return new LogiQLSubtypeConstraintEncoder(lattice);
    }

    @Override
    public EqualityConstraintEncoder<String> createEqualityConstraintEncoder() {
        return new LogiQLEqualityConstraintEncoder(lattice);
    }

    @Override
    public InequalityConstraintEncoder<String> createInequalityConstraintEncoder() {
        return new LogiQLInequalityConstraintEncoder(lattice);
    }

    @Override
    public ComparableConstraintEncoder<String> createComparableConstraintEncoder() {
        return new LogiQLComparableConstraintEncoder(lattice);
    }

    @Override
    public PreferenceConstraintEncoder<String> createPreferenceConstraintEncoder() {
        return null;
    }

    @Override
    public CombineConstraintEncoder<String> createCombineConstraintEncoder() {
        return null;
    }

    @Override
    public ExistentialConstraintEncoder<String> createExistentialConstraintEncoder() {
        return null;
    }

    @Override
    public ImplicationConstraintEncoder<String> createImplicationConstraintEncoder() {
        return null;
    }

    @Override
    public ArithmeticConstraintEncoder<String> createArithmeticConstraintEncoder() {
        return null;
    }
}
