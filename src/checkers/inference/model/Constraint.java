package checkers.inference.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Constraint {

    private List<Slot> slots = new ArrayList<Slot>();

    public Constraint(List<Slot> slots) {
        this.slots.addAll(slots);
    }

    public abstract Object serialize(Serializer serializer);

    public List<Slot> getSlots() {
        return slots;
    }
}
