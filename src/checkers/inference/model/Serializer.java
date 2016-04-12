package checkers.inference.model;

/**
 * Interface for serializing constraints and variables.
 *
 * Serialization will occur for all variables and constraints either before or instead of Constraint solving.
 * This allows us to avoid re-generating constraints for a piece of source code every time we wish to solve
 * (for instance when a new solver is written or an existing one is modified).
 */
public interface Serializer<S, T> {

    T serialize(SubtypeConstraint constraint);

    T serialize(EqualityConstraint constraint);

    T serialize(ExistentialConstraint constraint);

    T serialize(InequalityConstraint constraint);

    S serialize(VariableSlot slot);

    S serialize(ConstantSlot slot);

    S serialize(ExistentialVariableSlot slot);

    S serialize(RefinementVariableSlot slot);

    S serialize(CombVariableSlot slot);

    T serialize(ComparableConstraint comparableConstraint);

    T serialize(CombineConstraint combineConstraint);

    T serialize(PreferenceConstraint preferenceConstraint);
}
