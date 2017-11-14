package checkers.inference.util;

import checkers.inference.model.ConstantSlot;
import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

/**
 * This class verifies whether constraint between two ConstantSlots holds or not. We use this
 * class in early verification in {@link checkers.inference.model.ConstraintManager} and also
 * passes the reference to the instance of this class to {@link AbstractConstraintEncoder} so
 * that it can also use this util class to verify two-constants constraint for more efficient
 * encoding.
 *
 * @see checkers.inference.model.ConstraintManager
 * @see AbstractConstraintEncoder
 */
public class ConstraintVerifier {

    private final QualifierHierarchy realQualifierHierarchy;

    public ConstraintVerifier(QualifierHierarchy realQualifierHierarchy) {
        this.realQualifierHierarchy = realQualifierHierarchy;
    }

    public boolean isSubtype(ConstantSlot subType, ConstantSlot superType) {
        return realQualifierHierarchy.isSubtype(subType.getValue(), superType.getValue());
    }

    public boolean areEqual(ConstantSlot left, ConstantSlot right) {
        return AnnotationUtils.areSame(left.getValue(), right.getValue());
    }

    public boolean areComparable(ConstantSlot left, ConstantSlot right) {
        return isSubtype(left, right) || isSubtype(right, left);
    }
}
