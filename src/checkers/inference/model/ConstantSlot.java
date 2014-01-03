package checkers.inference.model;

import javax.lang.model.element.AnnotationMirror;

import annotations.io.ASTPath;

public class ConstantSlot extends Slot {
    
    private AnnotationMirror value;
    
    public ConstantSlot(AnnotationMirror value) {
        this.setValue(value);
    }
    
    public ConstantSlot(ASTPath astPath, AnnotationMirror value) {
        super(astPath);
        this.value = value;
    }

    @Override
    public Object serialize(Serializer serializer) {
        return serializer.serialize(this);
    }
    
    public AnnotationMirror getValue() {
        return value;
    }

    public void setValue(AnnotationMirror value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        ConstantSlot other = (ConstantSlot) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    } 
}
