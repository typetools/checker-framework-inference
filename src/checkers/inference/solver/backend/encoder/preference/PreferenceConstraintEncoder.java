package checkers.inference.solver.backend.encoder.preference;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.VariableSlot;

/**
 * Created by mier on 07/11/17.
 */
public interface PreferenceConstraintEncoder<ConstraintEncodingT> {

    ConstraintEncodingT encode(PreferenceConstraint constraint);
}
