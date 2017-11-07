package checkers.inference.util;

import checkers.inference.model.ConstantSlot;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

/**
 * This class verifies constraint with two ConstantSlots holds or not, because it
 * doesn't need solver involvment. We use this class in early verification in
 * ConstraintManager and also passes the reference to this class to solver engine
 * so that FormatTranslator can use this class to verify two-constants constraint
 * in order to encode it more efficiently.
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

    public boolean areInEqual(ConstantSlot left, ConstantSlot right) {
        return !areEqual(left, right);
    }

    public boolean areComparable(ConstantSlot left, ConstantSlot right) {
        return isSubtype(left, right) || isSubtype(right, left);
    }
}
