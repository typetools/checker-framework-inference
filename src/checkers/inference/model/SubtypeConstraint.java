package checkers.inference.model;

import java.util.Arrays;

public class SubtypeConstraint extends Constraint {

    private Slot subtype;
    private Slot supertype;
    
    public SubtypeConstraint(Slot subtype, Slot supertype) {
        super(Arrays.asList(subtype, supertype));
        this.subtype = subtype;
        this.supertype = supertype;
    }

    @Override
    public Object serialize(Serializer serializer) {
        return serializer.serialize(this);
    }
    
    public Slot getSubtype() {
        return subtype;
    }

    public void setSubtype(Slot subtype) {
        this.subtype = subtype;
    }

    public Slot getSupertype() {
        return supertype;
    }

    public void setSupertype(Slot supertype) {
        this.supertype = supertype;
    }
}
