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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((subtype == null) ? 0 : subtype.hashCode());
        result = prime * result
                + ((supertype == null) ? 0 : supertype.hashCode());
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
        SubtypeConstraint other = (SubtypeConstraint) obj;
        if (subtype == null) {
            if (other.subtype != null)
                return false;
        } else if (!subtype.equals(other.subtype))
            return false;
        if (supertype == null) {
            if (other.supertype != null)
                return false;
        } else if (!supertype.equals(other.supertype))
            return false;
        return true;
    }
}
