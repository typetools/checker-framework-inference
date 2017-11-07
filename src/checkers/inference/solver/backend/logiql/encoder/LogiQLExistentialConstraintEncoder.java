package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.model.ExistentialConstraint;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Created by mier on 07/11/17.
 */
public class LogiQLExistentialConstraintEncoder extends LogiQLAbstractConstraintEncoder implements ExistentialConstraintEncoder<String> {

    public LogiQLExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
    }

    @Override
    public String encode(ExistentialConstraint constraint) {
        return defaultEncoding();
    }
}
