package checkers.inference.solver.backend.logiql;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.backend.FormatTranslator;
import checkers.inference.solver.backend.logiql.encoder.LogiQLCombineConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLComparableConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLEqualityConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLExistentialConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLInEqualityConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLPreferenceConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLSubtypeConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * LogiQLFormatTranslator converts constraint into string as logiQL data.
 * 
 * @author jianchu
 *
 */
public class LogiQLFormatTranslator extends FormatTranslator<String, String, String> {

    public static final String emptyString = "";
    // TODO Jason can help adding domain specific String here
    protected static final String contradictoryString = "What String should model constradiction?";

    public LogiQLFormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
    }

    @Override
    protected final LogiQLSubtypeConstraintEncoder createSubtypeConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLSubtypeConstraintEncoder(lattice, verifier);
    }

    @Override
    protected final LogiQLEqualityConstraintEncoder createEqualityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLEqualityConstraintEncoder(lattice, verifier);
    }

    @Override
    protected final LogiQLInEqualityConstraintEncoder createInequalityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLInEqualityConstraintEncoder(lattice, verifier);
    }

    @Override
    protected final LogiQLComparableConstraintEncoder createComparableConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLComparableConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLPreferenceConstraintEncoder createPreferenceConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLPreferenceConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLCombineConstraintEncoder createCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLCombineConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLExistentialConstraintEncoder createExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new LogiQLExistentialConstraintEncoder(lattice, verifier);
    }

    @Override
    public AnnotationMirror decodeSolution(String solution, ProcessingEnvironment processingEnvironment) {
        // TODO Refactor LogiQL backend to follow the design protocal.
        return null;
    }

}
