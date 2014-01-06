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

    private Slot first;
    private Slot second;
    
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

    public void setFirst(Slot first) {
        this.first = first;
    }

    public Slot getSecond() {
        return second;
    }

    public void setSecond(Slot second) {
        this.second = second;
    }
}
