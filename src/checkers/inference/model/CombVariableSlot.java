package checkers.inference.model;

import annotations.io.ASTPath;

/**
 *  CombVariableSlots are used to model viewpoint adaptation.  CombVariableSlots represent
 *  locations whose values depend on two other VariableSlots.
 */
public class CombVariableSlot extends VariableSlot {

    private Slot first;
    private Slot second;
    
    public CombVariableSlot(ASTPath path, int id, Slot first, Slot second) {
        super(path, id);
        this.first = first;
        this.second = second;
    }

    @Override
    public Object serialize(Serializer serializer) {
        return serializer.serialize(this);
    }

    public Slot getFirst() {
        return first;
    }

    public void setFirst(Slot first) {
        this.first = first;
    }

    public Slot getSecond() {
        return second;
    }

    public void setSecond(Slot second) {
        this.second = second;
    }
}
