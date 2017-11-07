package checkers.inference.solver.backend.logiql.encoder;

import checkers.inference.model.PreferenceConstraint;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Created by mier on 07/11/17.
 */
public class LogiQLPreferenceConstraintEncoder extends LogiQLAbstractConstraintEncoder implements PreferenceConstraintEncoder<String> {

    public LogiQLPreferenceConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
    }

    @Override
    public String encode(PreferenceConstraint constraint) {
        // TODO Original implementation returns null. Are these two equivalnt?
        return defaultEncoding();
    }
}
