package checkers.inference.test;

import org.checkerframework.framework.test.TypecheckResult;

public class InferenceTestResult {
    private final InferenceTestConfiguration configuration;

    private final TypecheckResult initialTypecheckingResult;
    private final InferenceResult inferenceResult;
    private final InsertionResult insertionResult;
    private final TypecheckResult finalTypecheckResult;
    private final InferenceTestPhase lastPhaseRun;

    public InferenceTestResult(InferenceTestConfiguration configuration, TypecheckResult initialTypecheckingResult,
                               InferenceResult inferenceResult, InsertionResult insertionResult,
                               TypecheckResult finalTypecheckResult, InferenceTestPhase lastPhaseRun) {
        this.configuration = configuration;
        this.initialTypecheckingResult = initialTypecheckingResult;
        this.inferenceResult = inferenceResult;
        this.insertionResult = insertionResult;
        this.finalTypecheckResult = finalTypecheckResult;
        this.lastPhaseRun = lastPhaseRun;
    }

    public InferenceTestConfiguration getConfiguration() {
        return configuration;
    }

    public TypecheckResult getInitialTypecheckingResult() {
        return initialTypecheckingResult;
    }

    public InferenceResult getInferenceResult() {
        return inferenceResult;
    }

    public InsertionResult getInsertionResult() {
        return insertionResult;
    }

    public TypecheckResult getFinalTypecheckResult() {
        return finalTypecheckResult;
    }

    public InferenceTestPhase getLastPhaseRun() {
        return lastPhaseRun;
    }
}
