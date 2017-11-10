package checkers.inference.solver.backend.encoder.preference;

import checkers.inference.model.PreferenceConstraint;

/**
 * Preference constraint encoder. All concrete encoder that supports encoding PreferenceConstraint should
 * implement this interface.
 */
public interface PreferenceConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(PreferenceConstraint constraint);
}
