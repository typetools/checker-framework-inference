package checkers.inference.model;

import java.util.Arrays;

/**
 * Represents an equality relationship between two slots.
 * E.g.
 *  List<String> ls = new ArrayList<String>();
 *
 * If, in any type system:
 *    //vls represents the variable corresponding to the annotation on the first type String
 *    //located on the left-hand side of the assignment in List<String> ls ...
 *    vs = VariableSlot( astPathToVls, 0 )
 *
 *    //als represents the variable corresponding to the annotation on the second type String
 *    //located on the right-hand side of the assignment in ArrayList<String>()
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
public class EqualityConstraint extends Constraint {

    private final Slot first;
    private final Slot second;

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

    public Slot getSecond() {
        return second;
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
