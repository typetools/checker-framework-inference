package checkers.inference.model;

import annotations.io.ASTPath;

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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((first == null) ? 0 : first.hashCode());
        result = prime * result + ((second == null) ? 0 : second.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CombVariableSlot other = (CombVariableSlot) obj;
        if (first == null) {
            if (other.first != null)
                return false;
        } else if (!first.equals(other.first))
            return false;
        if (second == null) {
            if (other.second != null)
                return false;
        } else if (!second.equals(other.second))
            return false;
        return true;
    }
}
