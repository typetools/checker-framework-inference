package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * LogiQL side AbstractConstraintEncoder. It only contains logic how LogiQL models empty and
 * contradictory values.
 */
public class LogiQLAbstractConstraintEncoder extends AbstractConstraintEncoder<String> {

    private static String EMPTY_STRING = "";
    // TODO https://github.com/opprop/checker-framework-inference/issues/104
    private static String CONTRADICTORY_STRING = "What should go inside here?";

    public LogiQLAbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier, EMPTY_STRING, CONTRADICTORY_STRING);
    }
}
