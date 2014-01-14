package checkers.inference;

import java.util.List;

import javacutils.Pair;

import javax.lang.model.element.VariableElement;

import checkers.basetype.BaseAnnotatedTypeFactory;
import checkers.flow.CFAbstractAnalysis;
import checkers.flow.CFAnalysis;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;

// TODO: Placeholder file

public class InferenceAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    private InferrableChecker realChecker;
    private InferenceChecker inferenceChecker;

    public InferenceAnnotatedTypeFactory(
            InferenceChecker inferenceChecker,
            boolean withCombineConstraints,
            BaseAnnotatedTypeFactory realTypeFactory,
            InferrableChecker realChecker) {
        super(inferenceChecker, true);
        this.inferenceChecker = inferenceChecker;
        this.realChecker = realChecker;
    }

    @Override
    protected CFAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return realChecker.createInferenceAnalysis(inferenceChecker, this, fieldValues);
    }

    @Override
    public CFTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return realChecker.createInferenceTransferFunction(analysis);
    }
}
