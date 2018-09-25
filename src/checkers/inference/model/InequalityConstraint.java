package checkers.inference.model;

import java.util.Arrays;

import org.checkerframework.javacutil.BugInCF;

public class InequalityConstraint extends Constraint implements BinaryConstraint {

    private final Slot first;
    private final Slot second;

    private InequalityConstraint(Slot first, Slot second) {
        super(Arrays.asList(first, second));
        this.first = first;
        this.second = second;
    }

    private InequalityConstraint(Slot first, Slot second, AnnotationLocation location) {
        super(Arrays.asList(first, second), location);
        this.first = first;
        this.second = second;
    }

    protected static Constraint create(Slot first, Slot second, AnnotationLocation location) {
        if (first == null || second == null) {
            throw new BugInCF("Create inequality constraint with null argument. Subtype: "
                    + first + " Supertype: " + second);
        }

        // Normalization cases:
        // C1 != C2 => TRUE/FALSE depending on annotation
        // V == V => FALSE
        // otherwise => CREATE_REAL_INEQUALITY_CONSTRAINT

        // C1 != C2 => TRUE/FALSE depending on annotation
        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConst = (ConstantSlot) first;
            ConstantSlot secondConst = (ConstantSlot) second;

            return firstConst != secondConst
                    ? AlwaysTrueConstraint.create()
                    : AlwaysFalseConstraint.create();
        }

        // V == V => FALSE
        if (first == second) {
            return AlwaysFalseConstraint.create();
        }

        // otherwise => CREATE_REAL_INEQUALITY_CONSTRAINT
        return new InequalityConstraint(first, second, location);
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
        return new InequalityConstraint(first, second);
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
        InequalityConstraint other = (InequalityConstraint) obj;
        if ((first.equals(other.first) && second.equals(other.second))
                || (first.equals(other.second) && (second.equals(other.first)))) {
            return true;
        } else {
            return false;
        }
    }
}
