package checkers.inference.dataflow;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javacutils.Pair;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import checkers.flow.CFAbstractAnalysis;
import checkers.flow.CFAnalysis;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;
import checkers.inference.ConstraintManager;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.GenericAnnotatedTypeFactory;
import dataflow.cfg.node.Node;

/**
 * InferenceAnalysis makes a couple of changes to CFAnalysis:
 *
 * 1) Makes sure that only InferenceAnnotations are included in values.
 *
 * 2) Returns InferenceStore objects for createEmptyStore and createCopiedStore.
 *
 * @author mcarthur
 *
 * @param <Checker> The inference checker
 */
public class InferenceAnalysis extends CFAnalysis {

    private static final Logger logger = LoggerFactory.getLogger(InferenceAnalysis.class);
    private SlotManager slotManager;
    private ConstraintManager constraintManager;
    private InferrableChecker realChecker;

    public InferenceAnalysis(
            InferenceChecker checker,
            GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
            List<Pair<VariableElement, CFValue>> fieldValues,
            SlotManager slotManager,
            ConstraintManager constraintManager,
            InferrableChecker realChecker) {

        super(checker, factory, fieldValues);
        this.slotManager = slotManager;
        this.constraintManager = constraintManager;
        this.realChecker = realChecker;
    }

    @Override
    public CFValue defaultCreateAbstractValue(CFAbstractAnalysis<CFValue, ?, ?> analysis, AnnotatedTypeMirror aType) {

        Set<AnnotationMirror> inferenceAnnotations = new HashSet<AnnotationMirror>();
        for (Class<? extends Annotation> annoClass : InferenceChecker.getInferenceAnnotations()) {
            if (aType.getAnnotation(annoClass) != null) {
                inferenceAnnotations.add(aType.getAnnotation(annoClass));
            }
        }

        if (inferenceAnnotations.size() == 0) {
            logger.trace("Found aType with no inferenceAnnotations. Returning null");
            return null;
        } else {
            aType.clearAnnotations();
            aType.addAnnotations(inferenceAnnotations);
            return new InferenceValue((InferenceAnalysis) analysis, aType);
        }
    }

    @Override
    public InferenceStore createEmptyStore(boolean sequentialSemantics) {
        return new InferenceStore(this, sequentialSemantics);
    }

    @Override
    public InferenceStore createCopiedStore(CFStore other) {
        return new InferenceStore(this, other);
    }

    /**
     * Make nodeValues visible to the package, since InferenceTransfer changes it.
     * @return
     */
    protected IdentityHashMap<Node, CFValue> getNodeValues() {
        return nodeValues;
    }

    public SlotManager getSlotManager() {
        return slotManager;
    }

    public void setSlotManager(SlotManager slotManager) {
        this.slotManager = slotManager;
    }

    public ConstraintManager getConstraintManager() {
        return constraintManager;
    }

    public void setConstraintManager(ConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    public InferrableChecker getRealChecker() {
        return realChecker;
    }

    public void setRealChecker(InferrableChecker realChecker) {
        this.realChecker = realChecker;
    }
}