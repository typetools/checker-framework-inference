package checkers.inference.model;

import java.util.Arrays;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.BugInCF;

/**
 * Represents a constraint that two slots must be comparable.
 *
 */
public class ComparableConstraint extends Constraint implements BinaryConstraint {

    private final Slot first;
    private final Slot second;

    private ComparableConstraint(Slot first, Slot second, AnnotationLocation location) {
        super(Arrays.asList(first, second), location);
        this.first = first;
        this.second = second;
    }

    private ComparableConstraint(Slot first, Slot second) {
        super(Arrays.asList(first, second));
        this.first = first;
        this.second = second;
    }

    protected static Constraint create(Slot first, Slot second, AnnotationLocation location,
            QualifierHierarchy realQualHierarchy) {
        if (first == null || second == null) {
            throw new BugInCF("Create comparable constraint with null argument. Subtype: "
                    + first + " Supertype: " + second);
        }

        // Normalization cases:
        // C1 <~> C2 => TRUE/FALSE depending on relationship
        // V <~> V => TRUE (every type is always comparable to itself)
        // otherwise => CREATE_REAL_COMPARABLE_CONSTRAINT

        // C1 <~> C2 => TRUE/FALSE depending on relationship
        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConst = (ConstantSlot) first;
            ConstantSlot secondConst = (ConstantSlot) second;

            return realQualHierarchy.isSubtype(firstConst.getValue(), secondConst.getValue())
                    || realQualHierarchy.isSubtype(secondConst.getValue(), firstConst.getValue())
                            ? AlwaysTrueConstraint.create()
                            : AlwaysFalseConstraint.create();
        }

        // V <~> V => TRUE (every type is always comparable to itself)
        if (first == second) {
            return AlwaysTrueConstraint.create();
        }

        // otherwise => CREATE_REAL_COMPARABLE_CONSTRAINT
        return new ComparableConstraint(first, second, location);
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public Slot getFirst() {
        return first;
    }

    @Override
    public Slot getSecond() {
        return second;
    }

    @Override
    public Constraint make(Slot first, Slot second) {
        return new ComparableConstraint(first, second);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = result + ((first == null) ? 0 : first.hashCode());
        result = result + ((second == null) ? 0 : second.hashCode());
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
        ComparableConstraint other = (ComparableConstraint) obj;
        if ((first.equals(other.first) && second.equals(other.second))
                || (first.equals(other.second) && (second.equals(other.first)))) {
            return true;
        } else {
            return false;
        }
    }
}