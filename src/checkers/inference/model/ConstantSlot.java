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
}
