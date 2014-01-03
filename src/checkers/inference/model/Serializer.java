package checkers.inference.model;

public interface Serializer {

    Object serialize(SubtypeConstraint constraint);

    Object serialize(EqualityConstraint constraint);

    Object serialize(InequalityConstraint constraint);

    Object serialize(VariableSlot slot);

    Object serialize(ConstantSlot slot);
}
