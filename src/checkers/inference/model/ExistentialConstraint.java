package checkers.inference.model;

import org.checkerframework.framework.util.PluginUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ExistentialConstraint indicates that solvers need to determine if a variable's annotation
 * exists or not.  If that variable annotations exists then one set of constraints should be
 * enforced by the output solution, otherwise a different set of constraints should be enforce.
 * That is, they are constraints of the form:
 *
 * if (potentialVariable exists) {
 *     //enforce  potentialConstraints
 * } else {
 *     //enforce  alternateConstraints
 * }
 *
 * At the time of this writing, these constraints are used for uses of
 * generic type parameters exclusively.
 */
public class ExistentialConstraint extends Constraint {

    //A variable whose annotation may or may not exist
    private final VariableSlot potentialVariable;

    //The constraints to enforce if potentialVariable exists
    private final List<Constraint> potentialConstraints;

    //the constraints to enforce if potentialVariable DOES NOT exist
    private final List<Constraint> alternateConstraints;

    protected ExistentialConstraint(VariableSlot potentialVariable,
                                 List<Constraint> potentialConstraints,
                                 List<Constraint> alternateConstraints, AnnotationLocation location) {
        super(combineSlots(potentialVariable, potentialConstraints, alternateConstraints), location);
        this.potentialVariable = potentialVariable;
        this.potentialConstraints = Collections.unmodifiableList(potentialConstraints);
        this.alternateConstraints = Collections.unmodifiableList(alternateConstraints);
    }

    @SafeVarargs
    private static List<Slot> combineSlots(Slot potential, final List<Constraint> ... constraints) {
        final List<Slot> slots = new ArrayList<>();
        slots.add(potential);
        for (final List<Constraint> constraintList : constraints) {
            for (final Constraint constraint : constraintList) {
                slots.addAll(constraint.getSlots());
            }
        }
        return Collections.unmodifiableList(slots);
    }

    public VariableSlot getPotentialVariable() {
        return potentialVariable;
    }

    public List<Constraint> potentialConstraints() {
        return potentialConstraints;
    }

    public List<Constraint> getAlternateConstraints() {
        return alternateConstraints;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public String toString() {
        String tab = "    ";
        String doubleTab = tab + tab;
        return "ExistentialConstraint[\n"
                + tab + "if( " + potentialVariable + " ) {\n"
                + doubleTab + PluginUtil.join("\n" + doubleTab, potentialConstraints) + "\n"
                + tab + "} else {\n"
                + doubleTab + PluginUtil.join("\n" + doubleTab, alternateConstraints ) + "\n"
                + tab + "}\n"
                + "]";
    }
}
