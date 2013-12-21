package checkers.inference.model;

import java.util.Arrays;

public class EqualityConstraint extends Constraint {

    private Slot first;
    private Slot second;
    
    public EqualityConstraint(Slot first, Slot second) {
        super(Arrays.asList(first, second));
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
