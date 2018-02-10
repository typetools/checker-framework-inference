package checkers.inference.solver.backend.encoder;

import checkers.inference.solver.frontend.Lattice;

/**
 * Abstract base class for all concrete constraint encoders.
 *
 * {@link checkers.inference.model.Constraint} and constraint encoder are one-to-one relation.
 * Each concrete constraint encoder only supports encoding one type of {@code Constraint}.
 */
public abstract class AbstractConstraintEncoder<ConstraintEncodingT> {

    /**{@link Lattice} that is used to encode constraints.*/
    protected final Lattice lattice;

    /** Empty value that doesn't add additional restrictions to solver solution. Always satisfiable. */
    protected final ConstraintEncodingT emptyValue;

    /** A contradictory or always-false value that triggers solver to give no solution.*/
    protected final ConstraintEncodingT contradictoryValue;

    public AbstractConstraintEncoder(Lattice lattice, ConstraintEncodingT emptyValue,
            ConstraintEncodingT contradictoryValue) {
        this.lattice = lattice;
        this.emptyValue = emptyValue;
        this.contradictoryValue = contradictoryValue;
    }
}
