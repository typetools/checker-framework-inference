package checkers.inference.model;

/**
 * Interface for serializing constraints and variables.
 *
 * Serialization will occur for all variables and constraints either before or instead of Constraint solving.
 * This allows us to avoid re-generating constraints for a piece of source code every time we wish to solve
 * (for instance when a new solver is written or an existing one is modified).
 */
public interface Serializer {

    Object serialize(SubtypeConstraint constraint);

    Object serialize(EqualityConstraint constraint);

    Object serialize(ExistentialConstraint constraint);

    Object serialize(InequalityConstraint constraint);

    Object serialize(VariableSlot slot);

    Object serialize(ConstantSlot slot);

    Object serialize(ExistentialVariableSlot slot);

    Object serialize(RefinementVariableSlot slot);

    Object serialize(CombVariableSlot slot);

    Object serialize(ComparableConstraint comparableConstraint);

    Object serialize(CombineConstraint combineConstraint);

    Object serialize(PreferenceConstraint preferenceConstraint);
}
