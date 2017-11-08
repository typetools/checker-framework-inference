package checkers.inference.model;

/**
 * An exception that gets thrown if ConstraintManager sees a binary constraint between
 * two ConstantSlots doesn't hold. This ensures that guaranteed-to-fail constraints won't
 * go into solver phase thus it improves efficiency.
 */
public class UnsatisfiableTwoConstantException extends RuntimeException {
    public UnsatisfiableTwoConstantException(String s) {
        super(s);
    }
}
