package checkers.inference.model;

import java.util.Arrays;

/**
 * Represents a constraint that the viewpoint adaptation between target and decl gives result.
 *
 * <p>TODO: clarify relation to CombVariableSlot. Should we add separate types?
 */
public class CombineConstraint extends Constraint {

    private final Slot target;
    private final Slot decl;
    private final Slot result;

    public CombineConstraint(Slot target, Slot decl, Slot result) {
        super(Arrays.asList(target, decl, result));
        this.target = target;
        this.decl = decl;
        this.result = result;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public Slot getTarget() {
        return target;
    }

    public Slot getDeclared() {
        return decl;
    }

    public Slot getResult() {
        return result;
    }

    @Override
    public int hashCode() {
        int hc = 1;
        hc += ((target == null) ? 0 : target.hashCode());
        hc += ((decl == null) ? 0 : decl.hashCode());
        hc += ((result == null) ? 0 : result.hashCode());
        return hc;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        CombineConstraint other = (CombineConstraint) obj;
        if (target.equals(other.target) && decl.equals(other.decl) && result.equals(other.result)) {
            return true;
        } else {
            return false;
        }
    }
}
