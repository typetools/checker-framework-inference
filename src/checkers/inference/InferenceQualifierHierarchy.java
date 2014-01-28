package checkers.inference;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.util.InferenceUtil;
import checkers.util.MultiGraphQualifierHierarchy;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.Set;

/**
 * A qualifier hierarchy that generates constraints rather than evaluating them.  Calls to isSubtype
 * generates subtype and equality constraints between the input types based on the expected subtype
 * relationship (as described by the method signature).
 */
public class InferenceQualifierHierarchy extends MultiGraphQualifierHierarchy {
    private final InferenceMain inferenceMain = InferenceMain.getInstance();
    private final AnnotationMirror unqualified;

    public InferenceQualifierHierarchy(final MultiGraphFactory multiGraphFactory) {
        super(multiGraphFactory);
        final Set<? extends AnnotationMirror> tops = this.getTopAnnotations();
        assert tops.size() == 1 && tops.iterator().next().toString().equals("@checkers.quals.Unqualified") :
                "There should be only 1 top qualifier ( checkers.quals.Unqualified ).  " +
                "Tops found ( " + InferenceUtil.join(tops) + " )";
        unqualified = tops.iterator().next();
    }

    public boolean isSubtype(final Collection<? extends AnnotationMirror> rhsAnnos,
                             final Collection<? extends AnnotationMirror> lhsAnnos ) {
        assert rhsAnnos.size() == 1 && lhsAnnos.size() == 1 :
                "All types should have exactly 1 annotation! Annotations Types: " +
                "rhs ( " + InferenceUtil.join(rhsAnnos) + " ) lhs ( " + InferenceUtil.join(lhsAnnos) + " )";

        return isSubtype(rhsAnnos.iterator().next(), lhsAnnos.iterator().next());
    }

    public boolean isSubtype(final AnnotationMirror subtype, final AnnotationMirror supertype) {
        final SlotManager slotMgr = inferenceMain.getSlotManager();
        final ConstraintManager constrainMgr = inferenceMain.getConstraintManager();

        final Slot subSlot   = slotMgr.getSlot(subtype);
        final Slot superSlot = slotMgr.getSlot(supertype);
        if (!inferenceMain.isPerformingFlow()) {
            constrainMgr.add(new SubtypeConstraint(subSlot, superSlot));

        }

        return true;
    }

    public AnnotationMirror leastUpperBound(final AnnotationMirror a1, final AnnotationMirror a2) {
        assert a1 != null && a2 != null : "leastUpperBound accepts only NonNull types! 1 (" + a1 + " ) a2 (" + a2 + ")";

        final SlotManager slotMgr = inferenceMain.getSlotManager();
        final ConstraintManager constraintMgr = inferenceMain.getConstraintManager();
        if(inferenceMain.isPerformingFlow()) {
            //TODO: How to get the path to the CombVariable?
            final Slot slot1 = slotMgr.getSlot(a1);
            final Slot slot2 = slotMgr.getSlot(a2);
            final CombVariableSlot combVariableSlot = new CombVariableSlot(null, slotMgr.nextId(), slot1, slot2);
            slotMgr.addVariable( combVariableSlot );

            constraintMgr.add(new SubtypeConstraint(slot1, combVariableSlot));
            constraintMgr.add(new SubtypeConstraint(slot2, combVariableSlot));

            return slotMgr.getAnnotation(combVariableSlot);
        } else {
            return super.leastUpperBound(a1, a2);
        }
    }

    @Override
    public AnnotationMirror getTopAnnotation(final AnnotationMirror am) {
        return unqualified;
    }
}