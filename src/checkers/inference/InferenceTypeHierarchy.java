package checkers.inference;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.*;

import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import org.checkerframework.javacutil.ErrorReporter;

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
public class InferenceTypeHierarchy extends DefaultTypeHierarchy {
    //TODO: Think this through, add any missing constraints

    /**
     * Constructs an instance of {@code TypeHierarchy} for the type system
     * whose qualifiers represented in qualifierHierarchy.
     *
     * @param checker The type-checker to use
     * @param qualifierHierarchy The qualifier hierarchy to use
     */
    public InferenceTypeHierarchy(final BaseTypeChecker checker, final QualifierHierarchy qualifierHierarchy) {
        super(checker, qualifierHierarchy,
              checker.hasOption("ignoreRawTypeArguments"),
              checker.hasOption("invariantArrays"));
    }

    @Override
    public StructuralEqualityComparer createEqualityComparer() {
        return new InferenceEqualityComparer(rawnessComparer);
    }
}

class InferenceEqualityComparer extends StructuralEqualityComparer {

    public InferenceEqualityComparer(DefaultRawnessComparer rawnessComparer) {
        super(rawnessComparer);
        }

    @Override
    protected boolean arePrimeAnnosEqual(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2) {
        final InferenceMain inferenceMain = InferenceMain.getInstance();
        final Set<AnnotationMirror> t1Annos = type1.getAnnotations();
        final Set<AnnotationMirror> t2Annos = type2.getAnnotations();
        // TODO: HackMode
        if (InferenceMain.isHackMode() && t1Annos.size() != t2Annos.size() ) {
            InferenceMain.getInstance().logger.warning("Hack:InferenceTYpeHierarchy:60");
            return true;
        }

        assert t1Annos.size() == t2Annos.size() : "Mismatched type annotation sizes: rhs ( " + type1 + " ) lhs ( " + type2 + " ) ";

        if (t1Annos.size() > 0) {
            final AnnotationMirror leftAnno = t1Annos.iterator().next();
            final AnnotationMirror rightAnno = t2Annos.iterator().next();
        if (!inferenceMain.isPerformingFlow()) {
            final Slot leftSlot  = inferenceMain.getSlotManager().getSlot( leftAnno  );
            final Slot rightSlot = inferenceMain.getSlotManager().getSlot( rightAnno );
            inferenceMain.getConstraintManager().add(new EqualityConstraint(leftSlot, rightSlot));
        }
        } else if(!InferenceMain.isHackMode()) {
            ErrorReporter.errorAbort("Calling InferenceTypeHierarchy.arePrimeAnnosEqual on type with"
                                   + "no annotations.!\n"
                                   + "type1=" + type1 + "\n"
                                   + "type2=" + type2);
        } else {
            InferenceMain.getInstance().logger.warning("InferenceTYpeHierarchy:80");
        }
        return true;
    }
}