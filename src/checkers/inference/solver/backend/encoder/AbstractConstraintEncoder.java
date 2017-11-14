package checkers.inference.solver.backend.encoder;

import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;

/**
 * Abstract base class for all concrete constraint encoders.
 *
 * {@link checkers.inference.model.Constraint} and constraint encoder are one-to-one relation.
 * Each concrete constraint encoder only supports encoding one type of {@code Constraint}.
 */
public abstract class AbstractConstraintEncoder<ConstraintEncodingT> {

    /**{@link Lattice} that is used to encode constraints.*/
    protected final Lattice lattice;

    /**{@link ConstraintVerifier} that is used to verify whether a constraint between two
     * {@link checkers.inference.model.ConstantSlot}s holds or not.*/
    protected final ConstraintVerifier verifier;

    /** Empty value that doesn't add additional restrictions to solver solution. Always satisfiable. */
    protected final ConstraintEncodingT emptyValue;

    /** A contradictory or always-false value that triggers solver to give no solution.*/
    protected final ConstraintEncodingT contradictoryValue;

    public AbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier,
                                           ConstraintEncodingT emptyValue, ConstraintEncodingT contradictoryValue) {
        this.lattice = lattice;
        this.verifier = verifier;
        this.emptyValue = emptyValue;
        this.contradictoryValue = contradictoryValue;
    }
}
