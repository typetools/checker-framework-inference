package checkers.inference.solver.backend.encoder;

import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * The abstract super class that every constraint encoder should extend from. There is a one-to-one
 * relationship between Constraint and ConstraintEncoder. Each encoder only supports encoding its own
 * supported Constraint.
 */
public abstract class AbstractConstraintEncoder<ConstraintEncodingT> {

    protected final Lattice lattice;
    protected final ConstraintVerifier verifier;
    /** Empty value that doesn't add additional restrictions to solver solution. Always satisfiable */
    protected final ConstraintEncodingT emptyValue;
    /** A contradictory or always-false encoding. If injected to solver, solver will fail to give a solution*/
    protected final ConstraintEncodingT contradictoryValue;

    public AbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier,
                                           ConstraintEncodingT emptyValue, ConstraintEncodingT contradictoryValue) {
        this.lattice = lattice;
        this.verifier = verifier;
        this.emptyValue = emptyValue;
        this.contradictoryValue = contradictoryValue;
    }
}
