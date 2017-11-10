package checkers.inference.solver.backend.encoder.existential;

import checkers.inference.model.ExistentialConstraint;

/**
 * Existential constraint encoder. All concrete encoder that supports encoding ExistentialConstraint should
 * implement this interface.
 */
public interface ExistentialConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(ExistentialConstraint constraint);
}
