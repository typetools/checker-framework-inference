package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

/**
 * Created by mier on 07/11/17.
 */
public class LogiQLAbstractConstraintEncoder extends AbstractConstraintEncoder<String> {

    private static String emptyString = "";
    // TODO Jason can help
    private static String contradictoryString = "What should go inside here?";


    public LogiQLAbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier, emptyString, contradictoryString);
    }
}
