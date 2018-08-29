package checkers.inference.model;

import java.util.Arrays;
import org.checkerframework.javacutil.BugInCF;

/**
 * Represents a preference for a particular qualifier.
 */
public class PreferenceConstraint extends Constraint {

    private final VariableSlot variable;
    private final ConstantSlot goal;
    private final int weight;

    private PreferenceConstraint(VariableSlot variable, ConstantSlot goal, int weight,
            AnnotationLocation location) {
        super(Arrays.<Slot> asList(variable, goal), location);
        this.variable = variable;
        this.goal = goal;
        this.weight = weight;
    }

    protected static PreferenceConstraint create(VariableSlot variable, ConstantSlot goal,
            int weight, AnnotationLocation location) {
        if (variable == null || goal == null) {
            throw new BugInCF("Create preference constraint with null argument. Variable: "
                    + variable + " Goal: " + goal);
        }

        return new PreferenceConstraint(variable, goal, weight, location);
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public VariableSlot getVariable() {
        return variable;
    }

    public ConstantSlot getGoal() {
        return goal;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((variable == null) ? 0 : variable.hashCode());
        result = prime * result
                + ((goal == null) ? 0 : goal.hashCode());
        result = prime * result + weight;
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
        PreferenceConstraint other = (PreferenceConstraint) obj;
        if (variable == null) {
            if (other.variable != null) {
                return false;
            }
        } else if (!variable.equals(other.variable)) {
            return false;
        }

        if (goal == null) {
            if (other.goal != null) {
                return false;
            }
        } else if (!goal.equals(other.goal)) {
            return false;
        }

        return weight == other.weight;
    }
}
