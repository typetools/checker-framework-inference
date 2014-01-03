package checkers.inference.model;

import annotations.io.ASTPath;

public class VariableSlot extends Slot {

    private int id;

    public VariableSlot(ASTPath path, int id) {
        super(path);
        this.id = id;
    }

    @Override
    public Object serialize(Serializer serializer) {
        return serializer.serialize(this);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public VariableSlot(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
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
        VariableSlot other = (VariableSlot) obj;
        if (id != other.id)
            return false;
        return true;
    }
}
