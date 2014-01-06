package checkers.inference.model;

import java.util.Arrays;

/**
 * Represents a subtyping relationship between two slots.
 * E.g.
 *  String s = "yo";
 *  String a = s;
 *
 * If, using the Nullness type system:
 *    //vs represents the variable corresponding to the annotation on s
 *    vs = VariableSlot( astPathToS, 0 )
 *
 *    //va represents the variable corresponding to the annotation on a
 *    va = VariableSlot( astPathToA, 1 )
 *
 *    //cn represents the constant NonNull value (which "yo" inherently has)
 *    cnn = ConstantSlot( NonNull )
 *
 * Then:
 *   The above statements would result in the following SubtypeConstraints:
 *   logical representation:           in Java:
 *   vs <: cnn                         new SubtypeConstraint( vs, cnn )
 *   va <: vs                          new SubtypeConstraint( va, vs  )
 *
 */
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
