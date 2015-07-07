package checkers.inference.model;

/**
 * Implemented by constraints between two variables.
 */
public interface BinaryConstraint {
    public Slot getFirst();
    public Slot getSecond();

    public Constraint make(final Slot first, final Slot second);
}
