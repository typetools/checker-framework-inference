package checkers.inference.model;

/**
 * Implemented by constraints between two variables.
 */
public interface BinaryConstraint {
    Slot getFirst();
    Slot getSecond();

    Constraint make(final Slot first, final Slot second);
}
