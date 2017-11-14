package dataflow;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationBuilder;

import checkers.inference.BaseInferrableChecker;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstraintManager;
import dataflow.qual.DataFlow;
import dataflow.qual.DataFlowTop;

/**
 * Checker for Dataflow type system.
 * 
 * @author jianchu
 *
 */
public class DataflowChecker extends BaseInferrableChecker {
    public AnnotationMirror DATAFLOW, DATAFLOWTOP;

    @Override
    public void initChecker() {
        super.initChecker();
        setAnnotations();
    }

    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();
        DATAFLOW = AnnotationBuilder.fromClass(elements, DataFlow.class);
        DATAFLOWTOP = AnnotationBuilder.fromClass(elements, DataFlowTop.class);
    }

    @Override
    public DataflowVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory,
            boolean infer) {
        return new DataflowVisitor(this, ichecker, factory, infer);
    }

    @Override
    public DataflowAnnotatedTypeFactory createRealTypeFactory() {
        return new DataflowAnnotatedTypeFactory(this);
    }

    @Override
    public DataflowInferenceAnnotatedTypeFactory createInferenceATF(InferenceChecker inferenceChecker,
            InferrableChecker realChecker, BaseAnnotatedTypeFactory realTypeFactory,
            SlotManager slotManager, ConstraintManager constraintManager) {
        DataflowInferenceAnnotatedTypeFactory dataflowInferenceTypeFactory = new DataflowInferenceAnnotatedTypeFactory(
                inferenceChecker, realChecker.withCombineConstraints(), realTypeFactory, realChecker,
                slotManager, constraintManager);
        return dataflowInferenceTypeFactory;
    }
}