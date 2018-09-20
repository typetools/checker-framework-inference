package checkers.inference.model;

import java.util.Arrays;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.BugInCF;

/**
 * Represents a subtyping relationship between two slots.
 * E.g.
 *  String s = "yo";
 *  String a = s;
 *
 * If, using the Nullness type system:
 *    // vs represents the variable corresponding to the annotation on s
 *    vs = VariableSlot( astPathToS, 0 )
 *
 *    // va represents the variable corresponding to the annotation on a
 *    va = VariableSlot( astPathToA, 1 )
 *
 *    // cn represents the constant NonNull value (which "yo" inherently has)
 *    cnn = ConstantSlot( NonNull )
 *
 * Then:
 *   The above statements would result in the following SubtypeConstraints:
 *   logical representation:           in Java:
 *   vs <: cnn                         new SubtypeConstraint( vs, cnn )
 *   va <: vs                          new SubtypeConstraint( va, vs  )
 *
 */
public class SubtypeConstraint extends Constraint implements BinaryConstraint {

    private final Slot subtype;
    private final Slot supertype;

    private SubtypeConstraint(Slot subtype, Slot supertype, AnnotationLocation location) {
        super(Arrays.asList(subtype, supertype), location);
        this.subtype = subtype;
        this.supertype = supertype;
    }

    private SubtypeConstraint(Slot subtype, Slot supertype) {
        super(Arrays.asList(subtype, supertype));
        this.subtype = subtype;
        this.supertype = supertype;
    }

    protected static Constraint create(Slot subtype, Slot supertype, AnnotationLocation location,
            QualifierHierarchy realQualHierarchy) {
        if (subtype == null || supertype == null) {
            throw new BugInCF("Create subtype constraint with null argument. Subtype: "
                    + subtype + " Supertype: " + supertype);
        }

        // Normalization cases:
        // C1 <: C2 => TRUE/FALSE depending on relationship
        // V1 <: TOP => TRUE
        // V2 <: BOTTOM => REPLACE_WITH_EQUALITY
        // BOTTOM <: V2 => TRUE
        // TOP <: V2 => REPLACE_WITH_EQUALITY
        // V <: V => TRUE
        // otherwise => CREATE_REAL_SUBTYPE_CONSTRAINT

        // C1 <: C2 => TRUE/FALSE depending on relationship
        if (subtype instanceof ConstantSlot && supertype instanceof ConstantSlot) {
            ConstantSlot subConstant = (ConstantSlot) subtype;
            ConstantSlot superConstant = (ConstantSlot) supertype;

            return realQualHierarchy.isSubtype(subConstant.getValue(), superConstant.getValue())
                    ? AlwaysTrueConstraint.create()
                    : AlwaysFalseConstraint.create();
        }

        // C2
        if (supertype instanceof ConstantSlot) {
            if (isTop(realQualHierarchy, (ConstantSlot) supertype)) {
                // V1 <: TOP => TRUE
                return AlwaysTrueConstraint.create();
            } else if (isBottom(realQualHierarchy, (ConstantSlot) supertype)) {
                // V2 <: BOTTOM => REPLACE_WITH_EQUALITY
                return EqualityConstraint.create(subtype, supertype, location);
            }
        }

        // C1
        if (subtype instanceof ConstantSlot) {
            if (isBottom(realQualHierarchy, (ConstantSlot) subtype)) {
                // BOTTOM <: V2 => TRUE
                return AlwaysTrueConstraint.create();
            } else if (isTop(realQualHierarchy, (ConstantSlot) subtype)) {
                // TOP <: V2 => REPLACE_WITH_EQUALITY
                return EqualityConstraint.create(subtype, supertype, location);
            }
        }

        // V <: V => TRUE
        if (subtype == supertype) {
            return AlwaysTrueConstraint.create();
        }

        // The are no constant-constant cases in encoder
        // otherwise => CREATE_REAL_SUBTYPE_CONSTRAINT
        return new SubtypeConstraint(subtype, supertype, location);
    }

    private static boolean isTop(QualifierHierarchy realQualHierarchy, ConstantSlot slot) {
        return realQualHierarchy.getTopAnnotations().contains(slot.getValue());
    }

    private static boolean isBottom(QualifierHierarchy realQualHierarchy, ConstantSlot slot) {
        return realQualHierarchy.getBottomAnnotations().contains(slot.getValue());
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

    /**
     * @return getSubtype
     */
    @Override
    public Slot getFirst() {
        return getSubtype();
    }

    /**
     * @return getSupertype
     */
    @Override
    public Slot getSecond() {
        return getSupertype();
    }

    @Override
    public Constraint make(Slot first, Slot second) {
        return new SubtypeConstraint(first, second);
    }
}
