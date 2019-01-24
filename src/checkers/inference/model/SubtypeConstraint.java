package checkers.inference.model;

import java.util.Arrays;

/**
 * Represents a subtyping relationship between two slots.
 *
 * <p>E.g.
 *
 * <p>String s = "yo";
 *
 * <p>String a = s;
 *
 * <p>If, using the Nullness type system:
 *
 * <p>// vs represents the variable corresponding to the annotation on s
 *
 * <p>vs = VariableSlot( astPathToS, 0 )
 *
 * <p>// va represents the variable corresponding to the annotation on a
 *
 * <p>va = VariableSlot( astPathToA, 1 )
 *
 * <p>// cn represents the constant NonNull value (which "yo" inherently has)
 *
 * <p>cnn = ConstantSlot( NonNull )
 *
 * <p>Then:
 *
 * <p>The above statements would result in the following SubtypeConstraints:
 *
 * <p>logical representation:
 *
 * <p>vs <: cnn
 *
 * <p>va <: vs
 *
 * <p>in Java:
 *
 * <p>new SubtypeConstraint( vs, cnn )
 *
 * <p>new SubtypeConstraint( va, vs )
 */
public class SubtypeConstraint extends Constraint implements BinaryConstraint {

    private final Slot subtype;
    private final Slot supertype;

    public SubtypeConstraint(Slot subtype, Slot supertype) {
        super(Arrays.asList(subtype, supertype));
        this.subtype = subtype;
        this.supertype = supertype;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public Slot getSubtype() {
        return subtype;
    }

    public Slot getSupertype() {
        return supertype;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((subtype == null) ? 0 : subtype.hashCode());
        result = prime * result + ((supertype == null) ? 0 : supertype.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SubtypeConstraint other = (SubtypeConstraint) obj;
        if (subtype == null) {
            if (other.subtype != null) return false;
        } else if (!subtype.equals(other.subtype)) return false;
        if (supertype == null) {
            if (other.supertype != null) return false;
        } else if (!supertype.equals(other.supertype)) return false;
        return true;
    }

    /** @return getSubtype */
    @Override
    public Slot getFirst() {
        return getSubtype();
    }

    /** @return getSupertype */
    @Override
    public Slot getSecond() {
        return getSupertype();
    }

    @Override
    public Constraint make(Slot first, Slot second) {
        return new SubtypeConstraint(first, second);
    }
}
