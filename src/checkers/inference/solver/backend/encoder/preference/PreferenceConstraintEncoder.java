package checkers.inference.solver.backend.encoder.preference;

import checkers.inference.model.PreferenceConstraint;

/**
 * Interface that defines operations to encode a {@link checkers.inference.model.PreferenceConstraint}.
 */
public interface PreferenceConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(PreferenceConstraint constraint);
}
