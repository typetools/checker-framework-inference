package checkers.inference.model;

import java.util.Collections;
import org.checkerframework.javacutil.ErrorReporter;

/**
 * This "constraint" is the result of normalizing another constraint, where that constraint is
 * always false (evaluates to contradiction). This class is implemented as a singleton.
 *
 * @see {@link ConstraintManager}
 */
public class AlwaysFalseConstraint extends Constraint {

    private static AlwaysFalseConstraint singleton;

    private AlwaysFalseConstraint() {
        super(Collections.emptyList());
    }

    /** Creates/gets a singleton instance of the AlwaysFalseConstraint */
    protected static AlwaysFalseConstraint create() {
        if (singleton == null) {
            singleton = new AlwaysFalseConstraint();
        }
        return singleton;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        ErrorReporter.errorAbort(
                "Attempting to serialize an " + AlwaysFalseConstraint.class.getCanonicalName()
                        + ". This constraint should never be serialized.");
        return null;
    }
}
