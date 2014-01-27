package checkers.inference.dataflow;

import checkers.flow.CFAbstractStore;
import checkers.flow.CFStore;
import checkers.flow.CFValue;
import dataflow.analysis.FlowExpressions;

public class InferenceStore extends CFStore {

    public InferenceStore(InferenceAnalysis analysis, boolean sequentialSemantics) {
        super(analysis, sequentialSemantics);
    }

    public InferenceStore(InferenceAnalysis analysis, CFAbstractStore<CFValue, CFStore> other) {
        super(analysis, other);
    }

    /**
     * Don't want to remove any conflicts. Keep the refinement variables created on declaration.
     */
    @Override
    public void removeConflicting(FlowExpressions.FieldAccess fieldAccess, CFValue value) { }
}