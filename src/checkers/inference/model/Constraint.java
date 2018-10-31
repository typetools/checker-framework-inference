package checkers.inference.model;

import java.util.List;

/**
 * A Constraint represents a logical relationship between two or more Slots of any type.  ConstraintSolvers
 * assign values to VariableSlots such that the resulting assignments, if possible, satisfy all the given
 * Constraints.
 */
public abstract class Constraint {

    /**
     * The slots constrained by this object
     */
    private final List<Slot> slots;

    /**
     * Used to locate this constraint in source code.
     */
    private final AnnotationLocation location;

    public Constraint(List<Slot> slots, AnnotationLocation location) {
        // Instead of:
        //     List<Slot> newSlots = new ArrayList<Slot>(slots);
        //     this.slots = Collections.unmodifiableList(newSlots);
        // we create a direct alias. The users of the constructor
        // all use fresh lists.
        this.slots = slots;
        this.location = location;
    }

    public Constraint(List<Slot> slots) {
        // Instead of:
        // List<Slot> newSlots = new ArrayList<Slot>(slots);
        // this.slots = Collections.unmodifiableList(newSlots);
        // we create a direct alias. The users of the constructor
        // all use fresh lists.
        this.slots = slots;
        this.location = AnnotationLocation.MISSING_LOCATION;
    }

    public AnnotationLocation getLocation() {
        return location;
    }

    public abstract <S, T> T serialize(Serializer<S, T> serializer);

    /**
     * @return the list of slots referenced by this constraint
     */
    public List<Slot> getSlots() {
        return slots;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ": " + slots;
    }
}
