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
import checkers.inference.dataflow.InferenceAnalysis;

// TODO: Placeholder file

public class InferenceAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    private InferrableChecker realChecker;
    private InferenceChecker inferenceChecker;
    private SlotManager slotManager;
    private ConstraintManager constraintManager;

    public InferenceAnnotatedTypeFactory(
            InferenceChecker inferenceChecker,
            boolean withCombineConstraints,
            BaseAnnotatedTypeFactory realTypeFactory,
            InferrableChecker realChecker,
            SlotManager slotManager,
            ConstraintManager constraintManager) {

        super(inferenceChecker, true);
        this.inferenceChecker = inferenceChecker;
        this.realChecker = realChecker;
        this.slotManager = slotManager;
        this.constraintManager = constraintManager;
        postInit();
    }

    @Override
    protected CFAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return realChecker.createInferenceAnalysis(inferenceChecker, this, fieldValues, slotManager, constraintManager, realChecker);
    }

    @Override
    public CFTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return realChecker.createInferenceTransferFunction((InferenceAnalysis) analysis);
    }
}
