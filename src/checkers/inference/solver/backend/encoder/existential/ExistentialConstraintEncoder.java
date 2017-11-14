package checkers.inference.solver.backend.encoder.existential;

import checkers.inference.model.ExistentialConstraint;

/**
 * Interface that defines operations to encode a {@link checkers.inference.model.ExistentialConstraint}.
 */
public interface ExistentialConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(ExistentialConstraint constraint);
}
