package checkers.inference.solver.backend.encoder.existential;

import checkers.inference.model.ExistentialConstraint;

/**
 * Created by mier on 07/11/17.
 */
public interface ExistentialConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(ExistentialConstraint constraint);
}
