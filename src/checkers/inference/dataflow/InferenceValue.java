package checkers.inference.dataflow;

import java.util.Collections;
import java.util.Set;

import checkers.flow.CFValue;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.types.AnnotatedTypeMirror;

// TODO: Can we ever have constants here?
// TODO: Any better way of getting the position of a merge variable?

public class InferenceValue extends CFValue {


    public InferenceValue(InferenceAnalysis analysis, AnnotatedTypeMirror type) {
        super(analysis, type);
    }

    private InferenceAnalysis getInferenceAnalysis() {
        return (InferenceAnalysis) analysis;
    }

    @Override
    public CFValue leastUpperBound(CFValue other) {
        if (other == null) {
            return this;
        }

        AnnotatedTypeMirror otherType = other.getType();
        AnnotatedTypeMirror lubAnnotatedType = leastUpperBound(getType(), otherType);


        Slot slot1 = getInferenceAnalysis().getSlotManager().getSlot(getType());
        Slot slot2 = getInferenceAnalysis().getSlotManager().getSlot(other.getType());

        assert slot1 instanceof VariableSlot;
        assert slot2 instanceof VariableSlot;

        VariableSlot mergeSlot = createMergeVar((VariableSlot)slot1, (VariableSlot)slot2);
        lubAnnotatedType.replaceAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(mergeSlot));
        return analysis.createAbstractValue(lubAnnotatedType);
    }

    private VariableSlot createMergeVar(VariableSlot var1, VariableSlot var2) {
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

            RefinementVariableSlot newMergeVar = new RefinementVariableSlot(var1.getAstPath(),
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