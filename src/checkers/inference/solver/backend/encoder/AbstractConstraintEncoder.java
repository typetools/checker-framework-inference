package checkers.inference.solver.backend.encoder;

import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Created by mier on 07/11/17.
 */
public abstract class AbstractConstraintEncoder<ConstraintEncodingT> {

    protected final Lattice lattice;
    protected final ConstraintVerifier verifier;
    protected final ConstraintEncodingT emptyValue;
    // Clauses that are always false. Used to model unsatisfactory constraint between two
    // ConstantSlots.
    protected final ConstraintEncodingT contradictoryValue;

    public AbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier,
                                           ConstraintEncodingT emptyValue, ConstraintEncodingT contradictoryValue) {
        this.lattice = lattice;
        this.verifier = verifier;
        this.emptyValue = emptyValue;
        this.contradictoryValue = contradictoryValue;
    }

    protected final ConstraintEncodingT defaultEncoding() {
        return emptyValue;
    }
}
