package checkers.inference.dataflow;

import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;

/**
 * InferenceStore extends CFStore for inference.
 *
 * Currently it does not change the behaviour of CFStore.
 *
 * @author mcarthur
 *
 */
public class InferenceStore extends CFStore {

    public InferenceStore(InferenceAnalysis analysis, boolean sequentialSemantics) {
        super(analysis, sequentialSemantics);
    }

    public InferenceStore(InferenceAnalysis analysis, CFAbstractStore<CFValue, CFStore> other) {
        super(analysis, other);
    }

}