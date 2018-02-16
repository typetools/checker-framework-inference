package checkers.inference.model;

import java.util.Arrays;
import org.checkerframework.dataflow.util.HashCodeUtils;
import org.checkerframework.javacutil.ErrorReporter;
import com.sun.source.tree.Tree.Kind;

/**
 * Represents a constraint between the result of an arithmetic operation and its two operands.
 * Subclasses of this constraint class denote each specific kind of arithmetic operation, such as
 * addition, subtraction, multiplication, division, and modulus.
 */
public class ArithmeticConstraint extends Constraint {

    // Should this be defined within the constraint class, the slot class, or independently?
    public enum ArithmeticOperationKind {
        ADDITION("+"), SUBTRACTION("-"), MULTIPLICATION("*"), DIVISION("/"), MODULUS("%");

        // stores the symbol of the operation
        private final String opSymbol;

        private ArithmeticOperationKind(String opSymbol) {
            this.opSymbol = opSymbol;
        }

        public static ArithmeticOperationKind fromTreeKind(Kind kind) {
            switch (kind) {
                case PLUS:
                    return ADDITION;
                case MINUS:
                    return SUBTRACTION;
                case MULTIPLY:
                    return MULTIPLICATION;
                case DIVIDE:
                    return DIVISION;
                case REMAINDER:
                    return MODULUS;
                default:
                    return null;
            }
        }

        public String getSymbol() {
            return opSymbol;
        }
    }

    private final ArithmeticOperationKind operation;
    private final Slot leftOperand;
    private final Slot rightOperand;
    private final Slot result;

    private ArithmeticConstraint(ArithmeticOperationKind operation, Slot leftOperand,
            Slot rightOperand, Slot result, AnnotationLocation location) {
        super(Arrays.asList(leftOperand, rightOperand, result), location);
        this.operation = operation;
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.result = result;
    }

    protected static ArithmeticConstraint create(ArithmeticOperationKind operation,
            Slot leftOperand, Slot rightOperand, Slot result, AnnotationLocation location) {
        if (operation == null || leftOperand == null || rightOperand == null || result == null) {
            ErrorReporter.errorAbort("Create arithmetic constraint with null argument. "
                    + "Operation: " + operation + " LeftOperand: " + leftOperand + " RightOperand: "
                    + rightOperand + " Result: " + result);
        }

        return new ArithmeticConstraint(operation, leftOperand, rightOperand, result, location);
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

    public Slot getResult() {
        return result;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(operation, leftOperand, rightOperand, result);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ArithmeticConstraint other = (ArithmeticConstraint) obj;
        return operation.equals(other.operation) && leftOperand.equals(other.leftOperand)
                && rightOperand.equals(other.rightOperand) && result.equals(other.result);
    }
}
