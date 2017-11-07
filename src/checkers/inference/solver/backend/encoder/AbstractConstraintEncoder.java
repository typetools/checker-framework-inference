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
    // ConstantSlots. Here it uses 1,-1 in which 1 is almost always a variable id reserved
    // for real qualifier. I didn't find a clean way to pass number of expected variables
    // then plus 1 and use that here except adding a new field numberOfVariables in Lattice
    // class. But I'm not sure Lattice should care about numberOfVariables or not.
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
