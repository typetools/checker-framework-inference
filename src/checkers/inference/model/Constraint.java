package checkers.inference.model;

import java.util.ArrayList;
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
    private List<Slot> slots = new ArrayList<Slot>();

    public Constraint(List<Slot> slots) {
        this.slots.addAll(slots);
    }
    
    public abstract Object serialize(Serializer serializer);

    /**
     * @return the list of slots referenced by this constraint
     */
    public List<Slot> getSlots() {
        return slots;
    }
}
