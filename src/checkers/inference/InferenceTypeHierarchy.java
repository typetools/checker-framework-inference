package checkers.inference;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.TypeHierarchy;

import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;

/**
 *  The InferenceTypeHierarchy along with the InferenceQualifierHierarchy is responsible for
 *  creating a subtype and equality constraints. Normally the methods of these two classes are queried
 *  in order to verify that two types have a required subtype relationship or to determine what to do
 *  based on the relationship between the two types.  However, in the InferenceQualifierHierarchy
 *  calls to isSubtype generate subtype/equality constraints between the input parameters and returns true.
 *
 *  This class generally delegates calls to the InferenceQualifierHierarchy which in turn generates
 *  the correct constraints.
 */
public class InferenceTypeHierarchy extends TypeHierarchy {

    //TODO: Think through and add any missing constraints
    private InferenceMain inferenceMain = InferenceMain.getInstance();

    /**
     * Constructs an instance of {@code TypeHierarchy} for the type system
     * whose qualifiers represented in qualifierHierarchy.
     *
     * @param checker The type-checker to use
     * @param qualifierHierarchy The qualifier hierarchy to use
     */
    public InferenceTypeHierarchy(final BaseTypeChecker checker, final QualifierHierarchy qualifierHierarchy) {
        super(checker, qualifierHierarchy);
    }

    // copied from super, also allow type arguments with different qualifiers and create equality constraints
    @Override
    protected boolean isSubtypeAsTypeArgument(final AnnotatedTypeMirror rhs, final AnnotatedTypeMirror lhs) {
        // DO NOT COMMIT THIS.
        if (true) {
        return true;
        }

        if (lhs.getKind() == TypeKind.WILDCARD && rhs.getKind() != TypeKind.WILDCARD) {
            if (visited.contains(lhs))
                return true;

            visited.add(lhs);

            final AnnotatedTypeMirror lhsAsWildcard = ((AnnotatedTypeMirror.AnnotatedWildcardType)lhs).getExtendsBound();
            if (lhsAsWildcard == null)
                return true;

            return isSubtypeImpl(rhs, lhsAsWildcard);
        }

        if (lhs.getKind() == TypeKind.WILDCARD && rhs.getKind() == TypeKind.WILDCARD) {
            return isSubtype(((AnnotatedTypeMirror.AnnotatedWildcardType) rhs).getExtendsBound(),
                    ((AnnotatedTypeMirror.AnnotatedWildcardType) lhs).getExtendsBound());
        }

        if (lhs.getKind() == TypeKind.TYPEVAR && rhs.getKind() != TypeKind.TYPEVAR) {
            if (visited.contains(lhs)) {
                return true;
            }

            visited.add(lhs);
            return isSubtype(rhs, ((AnnotatedTypeMirror.AnnotatedTypeVariable) lhs).getUpperBound());
        }

        final Set<AnnotationMirror> lhsAnnos = lhs.getAnnotations();
        final Set<AnnotationMirror> rhsAnnos = rhs.getAnnotations();

        //TODO: Do something more intelligent with raw types?
        assert lhsAnnos.size() == rhsAnnos.size() : "Encountered raw types: rhs ( " + rhs + " ) lhs ( " + lhs + " ) ";

        //TODO: The original behavior was to check it and return true if size != 1
        assert lhsAnnos.size() == 1 : "Only 1 annotation expected.  Types: rhs ( " + rhs + " ) lhs ( " + lhs + " ) ";

        final AnnotationMirror leftAnno  = lhsAnnos.iterator().next();
        final AnnotationMirror rightAnno = rhsAnnos.iterator().next();

        if (!inferenceMain.isPerformingFlow()) {
            final Slot leftSlot  = inferenceMain.getSlotManager().getSlot( leftAnno  );
            final Slot rightSlot = inferenceMain.getSlotManager().getSlot( rightAnno );
            inferenceMain.getConstraintManager().add(new EqualityConstraint(leftSlot, rightSlot));
        }

        if (lhs.getKind() == TypeKind.DECLARED && rhs.getKind() == TypeKind.DECLARED) {
            return isSubtypeTypeArguments( (AnnotatedTypeMirror.AnnotatedDeclaredType) rhs, (AnnotatedTypeMirror.AnnotatedDeclaredType) lhs );

        } else if (lhs.getKind() == TypeKind.ARRAY && rhs.getKind() == TypeKind.ARRAY) {

            // arrays components within type arguments are invariants too
            // List<String[]> is not a subtype of List<Object[]>
            return isSubtypeAsTypeArgument(((AnnotatedTypeMirror.AnnotatedArrayType) rhs).getComponentType(),
                    ((AnnotatedTypeMirror.AnnotatedArrayType) lhs).getComponentType());
        }

        return true;
    }
}