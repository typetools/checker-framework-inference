package checkers.inference;

import java.util.*;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.util.InferenceUtil;

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
        assert tops.size() == 1 && tops.iterator().next().toString().equals("@org.checkerframework.framework.qual.Unqualified") :
                "There should be only 1 top qualifier ( org.checkerframework.framework.qual.Unqualified ).  " +
                "Tops found ( " + InferenceUtil.join(tops) + " )";
        unqualified = tops.iterator().next();
    }


    /**
     * Method to finalize the qualifier hierarchy before it becomes unmodifiable.
     * The parameters pass all fields and allow modification.
     */
    @Override
    protected void finish(QualifierHierarchy qualHierarchy,
                          Map<AnnotationMirror, Set<AnnotationMirror>> fullMap,
                          Map<AnnotationMirror, AnnotationMirror> polyQualifiers,
                          Set<AnnotationMirror> tops, Set<AnnotationMirror> bottoms,
                          Object... args) {

        AnnotationMirror unqualified = null;
        // Make only @Unqualified top
        Iterator<AnnotationMirror> it = tops.iterator();
        while (it.hasNext()) {
            AnnotationMirror anno = it.next();
            if (!anno.toString().endsWith("Unqualified")) {
                it.remove();
            } else if (unqualified == null) {
                unqualified = anno;
            }
        }

        // Make all annotations subtypes of @Unqualified
        for (Map.Entry<AnnotationMirror, Set<AnnotationMirror>> entry: fullMap.entrySet()) {
            if (entry.getKey() != unqualified && entry.getValue().size() == 0) {
                Set<AnnotationMirror> newSet = new HashSet<>(entry.getValue());
                newSet.add(unqualified);
                entry.setValue(Collections.unmodifiableSet(newSet));
            }
        }
    }

    /**
     * Overridden to prevent isSubtype call by just returning the first annotation.
     *
     * There should at most be 1 annotation on a type.
     *
     */
    @Override
    public AnnotationMirror findCorrespondingAnnotation(
            AnnotationMirror aliased, Collection<? extends AnnotationMirror> a) {
        if (a.size() == 0) {
            return null;
        } else if (a.size() == 1) {
            return a.iterator().next();
        } else {
            // TODO: DONT COMMIT!
            if (true) {
                return a.iterator().next();
            }

            ErrorReporter.errorAbort("Found type with multiple annotation mirrors: " + a);
            return null; // dead
        }
    }

    @Override
    public AnnotationMirror getAnnotationInHierarchy(
            Collection<? extends AnnotationMirror> annos, AnnotationMirror top) {
        if (annos.size() == 0) {
            return null;
        } else if (annos.size() == 1) {
            return annos.iterator().next();
        } else {
            ErrorReporter.errorAbort("Found type with multiple annotation mirrors: " + annos);
            return null; // dead
        }
    }

    @Override
    public boolean isSubtype(final Collection<? extends AnnotationMirror> rhsAnnos,
                             final Collection<? extends AnnotationMirror> lhsAnnos ) {
        assert rhsAnnos.size() == 1 && lhsAnnos.size() == 1 :
                "All types should have exactly 1 annotation! Annotations Types: " +
                "rhs ( " + InferenceUtil.join(rhsAnnos) + " ) lhs ( " + InferenceUtil.join(lhsAnnos) + " )";

        // TODO: DO NOT COMMIT THIS!!!!!!!!!!!!
        if (rhsAnnos.size() != 1 || lhsAnnos.size() != 1) {
            return true;
        }
        return isSubtype(rhsAnnos.iterator().next(), lhsAnnos.iterator().next());
    }

    @Override
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

    @Override
    public AnnotationMirror leastUpperBound(final AnnotationMirror a1, final AnnotationMirror a2) {
        assert a1 != null && a2 != null : "leastUpperBound accepts only NonNull types! 1 (" + a1 + " ) a2 (" + a2 + ")";

        final SlotManager slotMgr = inferenceMain.getSlotManager();
        final ConstraintManager constraintMgr = inferenceMain.getConstraintManager();
        if(inferenceMain.isPerformingFlow()) {
            //TODO: How to get the path to the CombVariable?
            final Slot slot1 = slotMgr.getSlot(a1);
            final Slot slot2 = slotMgr.getSlot(a2);
            if (slot1 != slot2) {
                final CombVariableSlot combVariableSlot = new CombVariableSlot(null, slotMgr.nextId(), slot1, slot2);
                slotMgr.addVariable(combVariableSlot);

                constraintMgr.add(new SubtypeConstraint(slot1, combVariableSlot));
                constraintMgr.add(new SubtypeConstraint(slot2, combVariableSlot));

                return slotMgr.getAnnotation(combVariableSlot);
            } else {
                return slotMgr.getAnnotation(slot1);
            }
        } else {
            return super.leastUpperBound(a1, a2);
        }
    }


    //================================================================================
    // TODO Both of these are probably wrong for inference. We really want a new VarAnnot for that position.
    //================================================================================
    @Override
    public AnnotationMirror getTopAnnotation(final AnnotationMirror am) {
        return unqualified;
    }
    @Override
    public AnnotationMirror getBottomAnnotation(final AnnotationMirror am) {
        return inferenceMain.getRealTypeFactory().getQualifierHierarchy().getBottomAnnotations().iterator().next();
    }
}