package checkers.inference.dataflow;

import checkers.inference.util.InferenceUtil;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * InferenceValue extends CFValue for inference.
 *
 * leastUpperBound, creates CombVariables to represent
 * the join of two VarAnnots.
 *
 * @author mcarthur
 *
 */
public class InferenceValue extends CFValue {


    public InferenceValue(InferenceAnalysis analysis, Set<AnnotationMirror> annotations, TypeMirror underlyingType) {
        super(analysis, annotations, underlyingType);
    }

    private InferenceAnalysis getInferenceAnalysis() {
        return (InferenceAnalysis) analysis;
    }

    /**
     * If values for a variable are not the same, create a merge variable to
     * represent the join of the two variables.
     *
     */
    @Override
    public CFValue leastUpperBound(CFValue other) {
        if (other == null) {
            return this;
        }

        final SlotManager slotManager = getInferenceAnalysis().getSlotManager();
        final QualifierHierarchy qualifierHierarchy = analysis.getTypeFactory().getQualifierHierarchy();

        Slot slot1 = getEffectiveSlot(this);
        Slot slot2 = getEffectiveSlot(other);

        AnnotationMirror anno1 = slotManager.getAnnotation(slot1);
        AnnotationMirror anno2 = slotManager.getAnnotation(slot2);

        // Delegate the LUB computation to inferenceQualifierHierarchy, by passing
        // the two VarAnnos getting from slotManager.
        final AnnotationMirror lub = qualifierHierarchy.leastUpperBound(anno1, anno2);

        return analysis.createAbstractValue(Collections.singleton(lub), getLubType(other, null));
    }

    public Slot getEffectiveSlot(final CFValue value) {
        if (value.getUnderlyingType().getKind() == TypeKind.TYPEVAR) {
            TypeVariable typevar = ((TypeVariable) value.getUnderlyingType());
            AnnotatedTypeVariable type =
                    (AnnotatedTypeVariable) analysis.getTypeFactory().getAnnotatedType(typevar.asElement());
            AnnotatedTypeMirror ubType = InferenceUtil.findUpperBoundType(type, InferenceMain.isHackMode());
            return getInferenceAnalysis().getSlotManager().getVariableSlot(ubType);
        }
        Iterator<AnnotationMirror> iterator = value.getAnnotations().iterator();
        AnnotationMirror annotationMirror = iterator.next();
        return getInferenceAnalysis().getSlotManager().getSlot(annotationMirror);
    }

    @Override
    public CFValue mostSpecific(CFValue other, CFValue backup) {

        if (other == null) {
            return this;
        } else {
            final TypeMirror underlyingType = getGlbType(other, backup);
            if (underlyingType.getKind() != TypeKind.TYPEVAR) {
                Slot thisSlot = getEffectiveSlot(this);
                Slot otherSlot = getEffectiveSlot(other);
                return mostSpecificFromSlot(thisSlot, otherSlot, other, backup);

            } else {
                return mostSpecificTypeVariable(underlyingType, other, backup);
            }
        }
    }

    /**
     * When inference looks up an identifier, it uses mostSpecific to determine
     * if the store value or the factory value should be used.
     *
     * Most specific must be overridden to ensure the correct annotation for a
     * variable for the block that it is in is used.
     *
     * With a declared type and its refinement variable, we want to use the refinement variable.
     *
     * If one variable has been merged to a comb variable, we want to use the comb
     * variable that was merged to.
     *
     * If any refinement variables for one variable has been merged to the other, we want the other.
     *
     */
    public CFValue mostSpecificFromSlot(final Slot thisSlot, final Slot otherSlot, final CFValue other, final CFValue backup) {
           if (thisSlot.isVariable() && otherSlot.isVariable()) {
               VariableSlot thisVarSlot = (VariableSlot) thisSlot;
               VariableSlot otherVarSlot = (VariableSlot) otherSlot;
               if (thisVarSlot.isMergedTo(otherVarSlot)) {
                   return other;
               } else if (otherVarSlot.isMergedTo(thisVarSlot)) {
                   return this;
               } else if (thisVarSlot instanceof RefinementVariableSlot
                       && ((RefinementVariableSlot) thisVarSlot).getRefined().equals(otherVarSlot)) {

                return this;
            } else if (otherVarSlot instanceof RefinementVariableSlot
                    && ((RefinementVariableSlot) otherVarSlot).getRefined().equals(thisVarSlot)) {

                return other;
            } else {
                // Check if one of these has refinement variables that were merged to the other.
                for (RefinementVariableSlot slot : thisVarSlot.getRefinedToSlots()) {
                    if (slot.isMergedTo(otherVarSlot)) {
                        return other;
                    }
                }
                for (RefinementVariableSlot slot : otherVarSlot.getRefinedToSlots()) {
                    if (slot.isMergedTo(thisVarSlot)) {
                        return this;
                    }
                }
            }
        }

        return backup;
    }

    public CFValue mostSpecificTypeVariable(TypeMirror resultType, CFValue other, CFValue backup) {
        final Types types = analysis.getTypeFactory().getProcessingEnv().getTypeUtils();
        final Slot otherSlot = getEffectiveSlot(other);
        final Slot thisSlot = getEffectiveSlot(this);

        final CFValue mostSpecificValue = mostSpecificFromSlot(thisSlot, otherSlot, other, backup);

        if (mostSpecificValue == backup) {
            return backup;
        }

        // result is type var T and the mostSpecific is type var T
        if (types.isSameType(resultType, mostSpecificValue.getUnderlyingType()))  {
            return mostSpecificValue;
        }

        // result is type var T but the mostSpecific is a type var U extends T
        // copy primary of U over to T
        final AnnotationMirror mostSpecificAnno =
                getInferenceAnalysis()
                    .getSlotManager()
                    .getAnnotation(mostSpecificValue == this ? thisSlot : otherSlot);


        AnnotatedTypeMirror resultAtm = AnnotatedTypeMirror.createType(resultType, analysis.getTypeFactory(), false);
        resultAtm.addAnnotation(mostSpecificAnno);
        return analysis.createAbstractValue(resultAtm);
    }

    private TypeMirror getLubType(final CFValue other, final CFValue backup) {

        // Create new full type (with the same underlying type), and then add
        // the appropriate annotations.
        TypeMirror underlyingType =
                TypesUtils.leastUpperBound(getUnderlyingType(),
                        other.getUnderlyingType(), analysis.getEnv());

        if (underlyingType.getKind() == TypeKind.ERROR
                || underlyingType.getKind() == TypeKind.NONE) {
            // pick one of the option
            if (backup != null) {
                underlyingType = backup.getUnderlyingType();
            } else {
                underlyingType = this.getUnderlyingType();
            }
        }

        return underlyingType;
    }

    private TypeMirror getGlbType(final CFValue other, final CFValue backup) {

        // Create new full type (with the same underlying type), and then add
        // the appropriate annotations.
        TypeMirror underlyingType =
                TypesUtils.greatestLowerBound(getUnderlyingType(),
                        other.getUnderlyingType(), analysis.getEnv());

        if (underlyingType.getKind() == TypeKind.ERROR
                || underlyingType.getKind() == TypeKind.NONE) {
            // pick one of the option
            if (backup != null) {
                underlyingType = backup.getUnderlyingType();
            } else {
                underlyingType = this.getUnderlyingType();
            }
        }

        return underlyingType;
    }
}
