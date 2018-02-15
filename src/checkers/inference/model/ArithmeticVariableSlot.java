package checkers.inference.model;

import org.checkerframework.dataflow.util.HashCodeUtils;
import org.checkerframework.javacutil.ErrorReporter;
import checkers.inference.model.ArithmeticConstraint.ArithmeticOperationKind;

/**
 * ArithmeticVariableSlot represent the result of an arithmetic operation between two other
 * {@link VariableSlot}s.
 */
public class ArithmeticVariableSlot extends VariableSlot {

    private final ArithmeticOperationKind operation;
    private final Slot leftOperand;
    private final Slot rightOperand;

    public ArithmeticVariableSlot(AnnotationLocation location, int id,
            ArithmeticOperationKind operation, Slot leftOperand, Slot rightOperand) {
        super(location, id);
        if (operation == null || leftOperand == null || rightOperand == null) {
            ErrorReporter.errorAbort("Create arithmetic variable slot with null argument. "
                    + "Operation: " + operation + " LeftOperand: " + leftOperand + " RightOperand: "
                    + rightOperand);
        }
        this.operation = operation;
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
    }

    @Override
    public Kind getKind() {
        return Kind.ARITHMETIC_VARIABLE;
    }

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public ArithmeticOperationKind getOperation() {
        return operation;
    }

    public Slot getLeftOperand() {
        return leftOperand;
    }

    public Slot getRightOperand() {
        return rightOperand;
    }

    /**
     * ArithmeticVariables should never be re-inserted into the source code.
     *
     * @return false
     */
    @Override
    public boolean isInsertable() {
        return false;
    }

    @Override
    public int hashCode() {
        // hash slot id, op, leftoperand, & rightoperand
        return HashCodeUtils.hash(super.hashCode(), operation, leftOperand, rightOperand);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        // super.equals checks that obj is not null, and checks slot id equality
        if (!super.equals(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ArithmeticVariableSlot other = (ArithmeticVariableSlot) obj;
        return operation.equals(other.operation) && leftOperand.equals(other.leftOperand)
                && rightOperand.equals(other.rightOperand);
    }
}
