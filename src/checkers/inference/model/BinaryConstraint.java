package checkers.inference.model;

/**
 * Implemented by constraints between two variables. Make is provided to be able to make a copy of
 * the constraint without knowing the concrete base class. getFirst and getSecond are provided so
 * that users can either copy through a slot to a constraint made by make or substitute out a
 * variable. E.g.,
 *
 * <p>this call makes a copy of the constraint
 *
 * <p>{@code make(getFirst(), getSecond())}
 *
 * <p>this call modifies the first slot
 *
 * <p>{@code make(mutate(getFirst()), getSecond())}
 */
public interface BinaryConstraint {
    Slot getFirst();

    Slot getSecond();

    /** Make a constraint that has the same class as this constraint but using the input slots. */
    Constraint make(final Slot first, final Slot second);
}
