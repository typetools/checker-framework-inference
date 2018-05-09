package checkers.inference.model;

import java.util.Arrays;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

/**
 * Represents an equality relationship between two slots.
 * E.g.
 *  List<String> ls = new ArrayList<String>();
 *
 * If, in any type system:
 *    // vls represents the variable corresponding to the annotation on the first type String
 *    // located on the left-hand side of the assignment in List<String> ls ...
 *    vs = VariableSlot( astPathToVls, 0 )
 *
 *    // als represents the variable corresponding to the annotation on the second type String
 *    // located on the right-hand side of the assignment in ArrayList<String>()
 *    va = VariableSlot( astPathToAls, 1 )
 *
 * Then:
 *   The above statements would result in the following EqualityConstraints:
 *   logical representation:           in Java:
 *   vls == als                        new EqualityConstraint( vls, als )
 *
 * Note: The equality relationship is commutative so order should not matter, though in practice
 * it is up to the given ConstraintSolver to treat them this way.
 */
public class EqualityConstraint extends Constraint implements BinaryConstraint {

    private final Slot first;
    private final Slot second;

    private EqualityConstraint(Slot first, Slot second) {
        super(Arrays.asList(first, second));
        this.first = first;
        this.second = second;
    }

    private EqualityConstraint(Slot first, Slot second, AnnotationLocation location) {
        super(Arrays.asList(first, second), location);
        this.first = first;
        this.second = second;
    }

    protected static Constraint create(Slot first, Slot second, AnnotationLocation location) {
        if (first == null || second == null) {
            ErrorReporter.errorAbort("Create equality constraint with null argument. Subtype: "
                    + first + " Supertype: " + second);
        }

        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConst = (ConstantSlot) first;
            ConstantSlot secondConst = (ConstantSlot) second;

            return AnnotationUtils.areSame(firstConst.getValue(), secondConst.getValue())
                    ? AlwaysTrueConstraint.create()
                    : AlwaysFalseConstraint.create();
        }

        return new EqualityConstraint(first, second, location);
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
        return new EqualityConstraint(first, second);
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
        EqualityConstraint other = (EqualityConstraint) obj;
        if ((first.equals(other.first) && second.equals(other.second))
                || (first.equals(other.second) && (second.equals(other.first)))) {
            return true;
        } else {
            return false;
        }
    }
}
