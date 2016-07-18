package checkers.inference;

import org.checkerframework.framework.qual.PolymorphicQualifier;
import org.checkerframework.framework.qual.Unqualified;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.PluginUtil;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.InferenceUtil;

/**
 * A qualifier hierarchy that generates constraints rather than evaluating them.  Calls to isSubtype
 * generates subtype and equality constraints between the input types based on the expected subtype
 * relationship (as described by the method signature).
 */
public class InferenceQualifierHierarchy extends MultiGraphQualifierHierarchy {
    private final InferenceMain inferenceMain = InferenceMain.getInstance();
    private final AnnotationMirror unqualified;
    private final AnnotationMirror varAnnot;

    private final SlotManager slotMgr;
    private final ConstraintManager constraintMgr;

    public InferenceQualifierHierarchy(final MultiGraphFactory multiGraphFactory) {
        super(multiGraphFactory);
        final Set<? extends AnnotationMirror> tops = this.getTopAnnotations();

        AnnotationMirror localUnqualified = null;
        AnnotationMirror localVarAnnot = null;
        for (AnnotationMirror top : tops) {
            if (isVarAnnot(top)) {
                localVarAnnot = top;
            } else {
                localUnqualified = top;
            }
        }
        unqualified = localUnqualified;
        varAnnot = localVarAnnot;

        if (unqualified == null) {
            ErrorReporter.errorAbort(
                    "Unqualified not found in the list of top annotations: tops=" + PluginUtil.join(", ", tops));
        }

        if (varAnnot == null) {
            ErrorReporter.errorAbort(
                    "VarAnnot not found in the list of top annotations: tops=" + PluginUtil.join(", ", tops));
        }

        if (tops.size() != 2) {
            ErrorReporter.errorAbort(
                    "There should be only 2 top qualifiers "
                 + "( org.checkerframework.framework.qual.Unqualified, checkers.inference.qual.VarAnnot ).\n"
                 + "Tops found ( " + InferenceUtil.join(tops) + " )"
            );
        }

        slotMgr = inferenceMain.getSlotManager();
        constraintMgr = inferenceMain.getConstraintManager();
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
        AnnotationMirror varAnnot = null;

        //@Unqualified should be the top of the "real" qualifier hierarchy when inferring
        //@VarAnnot should be a hierarchy unto itself
        Iterator<AnnotationMirror> it = tops.iterator();
        while (it.hasNext()) {
            AnnotationMirror anno = it.next();
            if (isUnqualified(anno)) {
                unqualified = anno;

            } else if (isVarAnnot(anno)) {
                varAnnot = anno;

            } else {
                it.remove();
            }
        }

        // Make all annotations subtypes of @Unqualified
        for (Map.Entry<AnnotationMirror, Set<AnnotationMirror>> entry: fullMap.entrySet()) {
            AnnotationMirror anno = entry.getKey();
            if (anno != unqualified && anno != varAnnot && entry.getValue().size() == 0) {
                Set<AnnotationMirror> newSet = new HashSet<>(entry.getValue());
                newSet.add(unqualified);
                entry.setValue(Collections.unmodifiableSet(newSet));
            }
        }
    }


    /**
     * @return true if anno is meta-annotated with PolymorphicQualifier
     */
    public static boolean isPolymorphic(AnnotationMirror anno) {
        //This is kind of an expensive way to compute this
        List<? extends AnnotationMirror> metaAnnotations = anno.getAnnotationType().asElement().getAnnotationMirrors();
        for (AnnotationMirror metaAnno : metaAnnotations) {
            if (metaAnno.getAnnotationType().toString().equals(PolymorphicQualifier.class.getCanonicalName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if anno is an instance of @VarAnnot
     */
    public static boolean isVarAnnot(AnnotationMirror anno) {
        if (InferenceMain.isHackMode(anno == null)) {
            return false;
        }

        return AnnotationUtils.areSameByClass(anno, VarAnnot.class);
    }

    /**
     * @return true if anno is an instance of @Unqualified
     */
    public static boolean isUnqualified(AnnotationMirror anno) {
        return AnnotationUtils.areSameByClass(anno, Unqualified.class);
    }

    /**
     * Overridden to prevent isSubtype call by just returning the first annotation.
     *
     * There should at most be 1 annotation on a type.
     *
     */
    @Override
    public AnnotationMirror findCorrespondingAnnotation(
            AnnotationMirror aliased, Collection<? extends AnnotationMirror> annos) {

        if (!annos.isEmpty()) {
            final AnnotationMirror anno = isVarAnnot(aliased) ? findVarAnnot(annos)
                                                              : findNonVarAnnot(annos);
            if (anno != null) {
                return anno;
            }
        }

        return null;

    }

    @Override
    public AnnotationMirror getAnnotationInHierarchy(
            Collection<? extends AnnotationMirror> annos, AnnotationMirror top) {

        if (!annos.isEmpty()) {
            final AnnotationMirror anno = isVarAnnot(top) ? findVarAnnot(annos)
                                                          : findNonVarAnnot(annos);
            if (anno != null) {
                return anno;
            }
        }

        return null;
    }

    /**
     * @return the first annotation in annos that is NOT an @VarAnnot
     */
    public static AnnotationMirror findNonVarAnnot(final Iterable<? extends AnnotationMirror> annos) {
        for (AnnotationMirror anno : annos) {
            if (!isVarAnnot(anno)) {
                return anno;
            }
        }

        return null;
    }

    /**
     * @return the first annotation in annos that IS an @VarAnnot
     */
    public static AnnotationMirror findVarAnnot(final Iterable<? extends AnnotationMirror> annos) {
        for (AnnotationMirror anno : annos) {
            if (InferenceMain.isHackMode(anno == null)) {
                continue;
            }

            if (isVarAnnot(anno)) {
                return anno;
            }
        }

        return null;
    }

    @Override
    public boolean isSubtype(final Collection<? extends AnnotationMirror> rhsAnnos,
                             final Collection<? extends AnnotationMirror> lhsAnnos ) {

        final AnnotationMirror rhsVarAnnot = findVarAnnot(rhsAnnos);
        final AnnotationMirror lhsVarAnnot = findVarAnnot(lhsAnnos);

        if (InferenceMain.isHackMode((rhsVarAnnot == null || lhsAnnos == null))) {
                InferenceMain.getInstance().logger.warning(
                    "Hack:InferenceQualifierHierarchy:165:\n"
                  + "    rhs=" + PluginUtil.join(", ", rhsAnnos) + "\n"
                  + "    lhs=" + PluginUtil.join(", ", lhsAnnos ));
                return true;
        }

        assert rhsVarAnnot != null && lhsVarAnnot != null :
                "All types should have exactly 1 VarAnnot!\n"
              + "    rhs=" + PluginUtil.join(", ", rhsAnnos) + "\n"
              + "    lhs=" + PluginUtil.join(", ", lhsAnnos );

        return isSubtype(rhsVarAnnot, lhsVarAnnot);
    }

    @Override
    public boolean isSubtype(final AnnotationMirror subtype, final AnnotationMirror supertype) {

        if (!isVarAnnot(subtype) || !isVarAnnot(supertype)) {
            return true;
        }

        final Slot subSlot   = slotMgr.getSlot(subtype);
        final Slot superSlot = slotMgr.getSlot(supertype);
        constraintMgr.add(new SubtypeConstraint(subSlot, superSlot));

        return true;
    }

    @Override
    public AnnotationMirror leastUpperBound(final AnnotationMirror a1, final AnnotationMirror a2) {
        if (InferenceMain.isHackMode( (a1 == null || a2 == null))) {
            InferenceMain.getInstance().logger.warning(
                    "Hack:InferenceQualifierHierarchy:204\n"
                  + "a1=" + a1 + "\n"
                  + "a2=" + a2);
            return a1 != null ? a1 : a2;
        }
        assert a1 != null && a2 != null : "leastUpperBound accepts only NonNull types! 1 (" + a1 + " ) a2 (" + a2 + ")";

        //for some reason LUB compares all annotations even if they are not in the same sub-hierarchy
        if (!isVarAnnot(a1)) {
            if (!isVarAnnot(a2)) {
                return super.leastUpperBound(a1, a2);
            } else {
                return null;
            }
        } else if (!isVarAnnot(a2)) {
            return null;
        }

        //TODO: How to get the path to the CombVariable?
        final Slot slot1 = slotMgr.getSlot(a1);
        final Slot slot2 = slotMgr.getSlot(a2);
        if (slot1 != slot2) {
            final CombVariableSlot mergeVariableSlot = slotMgr.createCombVariableSlot(slot1, slot2);

            constraintMgr.add(new SubtypeConstraint(slot1, mergeVariableSlot));
            constraintMgr.add(new SubtypeConstraint(slot2, mergeVariableSlot));

            return slotMgr.getAnnotation(mergeVariableSlot);
        } else {
            return slotMgr.getAnnotation(slot1);
        }
    }


    //================================================================================
    // TODO Both of these are probably wrong for inference. We really want a new VarAnnot for that position.
    //================================================================================

    @Override
    public AnnotationMirror getTopAnnotation(final AnnotationMirror am) {
        if (isVarAnnot(am)) {
            return varAnnot;
        } //else

        return unqualified;
    }

    @Override
    public AnnotationMirror getBottomAnnotation(final AnnotationMirror am) {
        if (isVarAnnot(am)) {
            return varAnnot;
        } //else

        return inferenceMain.getRealTypeFactory().getQualifierHierarchy().getBottomAnnotations().iterator().next();
    }
}