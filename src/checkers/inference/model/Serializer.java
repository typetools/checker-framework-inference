package checkers.inference.model;

/**
 * Interface for serializing constraints and variables.
 *
 * Serialization will occur for all variables and constraints either
 * before or instead of Constraint solving.
 *
 * This allows us to avoid re-generating constraints for a piece of
 * source code every time we wish to solve (for instance when a new
 * solver is written or an existing one is modified).
 *
 * Type parameters S and T are used to adapt the return type of the
 * XXXSlot visitor methods (S) and the XXXConstraint visitor methods
 * (T).
 * Implementing classes can use the same or different types for these
 * type parameters.
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

    S serialize(LubVariableSlot slot);

    T serialize(ComparableConstraint comparableConstraint);

    T serialize(CombineConstraint combineConstraint);

    T serialize(PreferenceConstraint preferenceConstraint);

    T serialize(ArithmeticConstraint arithmeticConstraint);
}
