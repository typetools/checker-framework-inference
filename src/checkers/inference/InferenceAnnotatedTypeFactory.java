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
import dataflow.analysis.TransferFunction;

// TODO: Placeholder file

public class InferenceAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    public InferenceAnnotatedTypeFactory(
            InferenceChecker inferenceChecker,
            boolean withCombineConstraints,
            BaseAnnotatedTypeFactory realTypeFactory) {
        super(inferenceChecker, true);

    }

    @Override
    protected CFAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return InferenceMain.getInstance().createFlowAnalysis(fieldValues);
    }

    @Override
    public CFTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return InferenceMain.getInstance().createTransferFunction(analysis);
    }
}
