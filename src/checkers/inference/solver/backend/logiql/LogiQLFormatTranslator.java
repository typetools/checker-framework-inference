package checkers.inference.solver.backend.logiql;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.backend.AbstractFormatTranslator;
import checkers.inference.solver.backend.logiql.encoder.LogiQLComparableConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLEqualityConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLInequalityConstraintEncoder;
import checkers.inference.solver.backend.logiql.encoder.LogiQLSubtypeConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * LogiQLFormatTranslator converts constraint into string as logiQL data.
 * 
 * @author jianchu
 *
 */
public class LogiQLFormatTranslator extends AbstractFormatTranslator<String, String, String> {

    public LogiQLFormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
        postInit();
    }

    @Override
    protected LogiQLSubtypeConstraintEncoder createSubtypeConstraintEncoder(ConstraintVerifier verifier) {
        return new LogiQLSubtypeConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLEqualityConstraintEncoder createEqualityConstraintEncoder(ConstraintVerifier verifier) {
        return new LogiQLEqualityConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLInequalityConstraintEncoder createInequalityConstraintEncoder(ConstraintVerifier verifier) {
        return new LogiQLInequalityConstraintEncoder(lattice, verifier);
    }

    @Override
    protected LogiQLComparableConstraintEncoder createComparableConstraintEncoder(ConstraintVerifier verifier) {
        return new LogiQLComparableConstraintEncoder(lattice, verifier);
    }

    @Override
    public AnnotationMirror decodeSolution(String solution, ProcessingEnvironment processingEnvironment) {
        // TODO Refactor LogiQL backend to follow the design protocal.
        return null;
    }

}
