package checkers.inference.dataflow;

import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.InternalUtils;

import java.util.Collections;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.InferenceUtil;

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


    public InferenceValue(InferenceAnalysis analysis, AnnotatedTypeMirror type) {
        super(analysis, type);
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

        if (slot1 instanceof ConstantSlot && slot2 instanceof ConstantSlot) {
            final AnnotationMirror lub = qualifierHierarchy.leastUpperBound(((ConstantSlot) slot1).getValue(), ((ConstantSlot) slot2).getValue());

            //keep the annotations in the Unqualified/real type system
            AnnotatedTypeMirror returnType = getType().shallowCopy(true);
            returnType.replaceAnnotation(lub);
            return analysis.createAbstractValue(returnType);

        } else {

            VariableSlot mergeSlot = createMergeVar(slot1, slot2);
            if (InferenceMain.isHackMode(mergeSlot == null)) {
                AnnotatedTypeMirror returnType = getType().shallowCopy(false);
                return analysis.createAbstractValue(returnType);
            }
            //keep the annotations in the Unqualified/real type system
            AnnotatedTypeMirror returnType = getType().shallowCopy(true);
            returnType.replaceAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(mergeSlot));
            return analysis.createAbstractValue(returnType);
        }
    }

    /**
     * Create a variable to represent the join of var1 and var2.
     *
     * If slot1 and slot2 have been merged before or if one has been
     * merged to another (or transitively merged to another), return
     * the variable that was merged into.
     *
     * @return The merge variable.
     */
    private VariableSlot createMergeVar(Slot slot1, Slot slot2) {

        if (slot1 instanceof ConstantSlot || slot2 instanceof ConstantSlot) {
            // This currently happens for merging intializers on fields: CFAbstractTransfer.initialStore

            CombVariableSlot newMergeVar =  getInferenceAnalysis().getSlotManager().createCombVariableSlot(slot1, slot2);

            // Lub of the two
            getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(slot1, newMergeVar));
            getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(slot2, newMergeVar));

            return newMergeVar;
        } else {

            VariableSlot var1 = (VariableSlot) slot1;
            VariableSlot var2 = (VariableSlot) slot2;
            if (var1 == var2) {
                // These are the same variable
                return var1;

            } else if (!Collections.disjoint(var1.getMergedToSlots(), var2.getMergedToSlots())) {
                // There is a chain that merges var1 and var2
                return getOneIntersected(var1.getMergedToSlots(), var2.getMergedToSlots());

            } else if (var1.isMergedTo(var2)) {
                // Var2 is a merge varaible that var1 has been merged to
                return var2;

            } else if (var2.isMergedTo(var1)) {
                return var1;

            } else {

                CombVariableSlot newMergeVar = getInferenceAnalysis().getSlotManager().createCombVariableSlot(var1, var2);

                var1.getMergedToSlots().add(newMergeVar);
                var2.getMergedToSlots().add(newMergeVar);

                // newMergeVar must be the supertype of var1 and var2.
                getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(var1, newMergeVar));
                getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(var2, newMergeVar));

                return newMergeVar;
            }
        }
    }


    public Slot getEffectiveSlot(final CFValue value) {
        final AnnotatedTypeMirror type = value.getType();
        if (type.getKind() == TypeKind.TYPEVAR) {
            final AnnotatedTypeMirror ubType = InferenceUtil.findUpperBoundType((AnnotatedTypeVariable)type, InferenceMain.isHackMode());
            return getInferenceAnalysis().getSlotManager().getVariableSlot(ubType);
        } else {
            return getInferenceAnalysis().getSlotManager().getVariableSlot(type);
        }
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

        //result is type var T and the mostSpecific is type var T
        if (types.isSameType(resultType, mostSpecificValue.getType().getUnderlyingType()))  {
            return mostSpecificValue;
        }

        //result is type var T but the mostSpecific is a type var U extends T
        //copy primary of U over to T
        final AnnotationMirror mostSpecificAnno =
                getInferenceAnalysis()
                    .getSlotManager()
                    .getAnnotation(mostSpecificValue == this ? thisSlot : otherSlot);


        AnnotatedTypeMirror resultAtm = AnnotatedTypeMirror.createType(resultType, analysis.getTypeFactory(), false);
        resultAtm.addAnnotation(mostSpecificAnno);
        return analysis.createAbstractValue(resultAtm);
    }

    /**
     * TODO: Should we replace this with guava?
     *
     * @return The first element found in both set1 and set2.
     */
    private <T> T getOneIntersected(Set<T> set1, Set<T> set2) {
        for (T refVar : set1) {
            if (set2.contains(refVar)) {
                return refVar;
            }
        }
        return null;
    }

    private TypeMirror getLubType(final CFValue other, final CFValue backup) {

        // Create new full type (with the same underlying type), and then add
        // the appropriate annotations.
        TypeMirror underlyingType =
                InternalUtils.leastUpperBound(analysis.getEnv(),
                        getType().getUnderlyingType(), other.getType().getUnderlyingType());

        if (underlyingType.getKind() == TypeKind.ERROR
                || underlyingType.getKind() == TypeKind.NONE) {
            // pick one of the option
            if (backup != null) {
                underlyingType = backup.getType().getUnderlyingType();
            } else {
                underlyingType = this.getType().getUnderlyingType();
            }
        }

        return underlyingType;
    }

    private TypeMirror getGlbType(final CFValue other, final CFValue backup) {

        // Create new full type (with the same underlying type), and then add
        // the appropriate annotations.
        TypeMirror underlyingType =
                InternalUtils.greatestLowerBound(analysis.getEnv(),
                        getType().getUnderlyingType(), other.getType().getUnderlyingType());

        if (underlyingType.getKind() == TypeKind.ERROR
                || underlyingType.getKind() == TypeKind.NONE) {
            // pick one of the option
            if (backup != null) {
                underlyingType = backup.getType().getUnderlyingType();
            } else {
                underlyingType = this.getType().getUnderlyingType();
            }
        }

        return underlyingType;
    }
}