package checkers.inference;

import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.StructuralEqualityComparer;
import org.checkerframework.framework.type.StructuralEqualityVisitHistory;
import org.checkerframework.javacutil.BugInCF;

import javax.lang.model.element.AnnotationMirror;

class InferenceEqualityComparer extends StructuralEqualityComparer {

    private final AnnotationMirror varAnnot;

    public InferenceEqualityComparer(StructuralEqualityVisitHistory typeargVisitHistory, AnnotationMirror varAnnot) {
        super(typeargVisitHistory);
        this.varAnnot = varAnnot;
    }

    @Override
    protected boolean arePrimeAnnosEqual(AnnotatedTypeMirror type1, AnnotatedTypeMirror type2) {
        final InferenceMain inferenceMain = InferenceMain.getInstance();
        final AnnotationMirror varAnnot1 = type1.getAnnotationInHierarchy(varAnnot);
        final AnnotationMirror varAnnot2 = type2.getAnnotationInHierarchy(varAnnot);

        // TODO: HackMode
        if (InferenceMain.isHackMode((varAnnot1 == null || varAnnot2 == null))) {
            InferenceMain.getInstance().logger.warning(
                    "Hack:InferenceTYpeHierarchy:66\n"
                            + "type1=" + type1 + "\n"
                            + "type2=" + type2 + "\n"
            );
            return true;
        }

        if (varAnnot1 == null || varAnnot2 == null) {
            throw new BugInCF("Calling InferenceTypeHierarchy.arePrimeAnnosEqual on type with"
                    + "no varAnnots.!\n"
                    + "type1=" + type1 + "\n"
                    + "type2=" + type2);
        }

        if (!inferenceMain.isPerformingFlow()) {
            final Slot leftSlot  = inferenceMain.getSlotManager().getSlot( varAnnot1 );
            final Slot rightSlot = inferenceMain.getSlotManager().getSlot( varAnnot2 );
            inferenceMain.getConstraintManager().add(new EqualityConstraint(leftSlot, rightSlot));
        }

        return true;
    }
}
