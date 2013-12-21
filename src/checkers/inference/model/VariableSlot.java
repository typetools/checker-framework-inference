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
}
