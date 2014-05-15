package checkers.inference.dataflow;

import java.util.Collections;
import java.util.Set;

import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
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

        Slot slot1 = getInferenceAnalysis().getSlotManager().getSlot(getType());
        Slot slot2 = getInferenceAnalysis().getSlotManager().getSlot(other.getType());

        if (slot1 instanceof ConstantSlot && slot2 instanceof ConstantSlot) {
            // TODO: Need to investigate more on the interaction with constants
            if (((ConstantSlot)slot1).getValue() != ((ConstantSlot)slot2).getValue()) {
                ErrorReporter.errorAbort("Dataflow merged two different constant values!");
            }

            AnnotatedTypeMirror returnType = getType().getCopy(false);
            returnType.replaceAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(slot1));
            return analysis.createAbstractValue(returnType);

        } else {

            VariableSlot mergeSlot = createMergeVar(slot1, slot2);
            AnnotatedTypeMirror returnType = getType().getCopy(false);
            returnType.replaceAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(mergeSlot));
            return analysis.createAbstractValue(returnType);
        }
    }

    /**
     * Create a variable to represent the join of var1 and var2.
     *
     * If var1 and var2 have been merged before or if one has been
     * merged to another (or transitively merged to another), return
     * the variable that was merged into.
     *
     * @param var1
     * @param var2
     * @return The merge variable.
     */
    private VariableSlot createMergeVar(Slot slot1, Slot slot2) {

        if (slot1 instanceof ConstantSlot || slot2 instanceof ConstantSlot) {
            // This currently happens for merging intializers on fields: CFAbstractTransfer.initialStore

            CombVariableSlot newMergeVar = new CombVariableSlot(slot1.getASTRecord(),
                    getInferenceAnalysis().getSlotManager().nextId(), slot1, slot2);

            getInferenceAnalysis().getSlotManager().addVariable(newMergeVar);

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

                CombVariableSlot newMergeVar = new CombVariableSlot(var1.getASTRecord(),
                        getInferenceAnalysis().getSlotManager().nextId(), var1, var2);

                getInferenceAnalysis().getSlotManager().addVariable(newMergeVar);
                var1.getMergedToSlots().add(newMergeVar);
                var2.getMergedToSlots().add(newMergeVar);

                // newMergeVar must be the supertype of var1 and var2.
                getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(var1, newMergeVar));
                getInferenceAnalysis().getConstraintManager().add(new SubtypeConstraint(var2, newMergeVar));

                return newMergeVar;
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
    @Override
    public CFValue mostSpecific(CFValue other, CFValue backup) {

        if (other == null) {
            return this;
        } else {
            Slot thisSlot = getInferenceAnalysis().getSlotManager().getSlot(getType());
            Slot otherSlot = getInferenceAnalysis().getSlotManager().getSlot(other.getType());
            if (thisSlot instanceof VariableSlot && otherSlot instanceof VariableSlot) {
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
        }

        return backup;
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
}