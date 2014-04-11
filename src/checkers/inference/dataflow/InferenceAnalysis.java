package checkers.inference.dataflow;

import java.util.IdentityHashMap;
import java.util.List;

import javax.lang.model.element.VariableElement;

import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import checkers.inference.ConstraintManager;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;

/**
 * InferenceAnalysis tweaks dataflow for Checker-Framework-Inference.
 *
 * Checker-Framework-Inference's dataflow is primarily concerned with the creation
 * and maintenance of RefinementVariableSlots. (See RefinementVaraibleSlots).
 *
 * InferenceAnalysis returns InferenceStore for createEmptyStore and createCopiedStore.
 * This is what makes the InferenceStore be the store used when the dataflow algorithm is
 * executed by the type factory.
 *
 * InferenceAnalysis also holds references to other inference components (SlotManager, ConstraintManager, etc.)
 * to make them available to other inference dataflow components.
 *
 * Finally, InferenceAnalysis make analysis' nodeValues field available outside of the class. InferenceTransfer
 * uses nodeValue to override values for nodes.
 *
 * @author mcarthur
 *
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

    /**
     * Validate that a type has at most 1 annotation.
     *
     * Null types will be returned when a type has no annotations. This happens currently when getting
     * the declaration of a class.
     */
    @Override
    public CFValue defaultCreateAbstractValue(CFAbstractAnalysis<CFValue, ?, ?> analysis, AnnotatedTypeMirror aType) {

        if (aType.getAnnotations().size() == 0) {
            // This happens for currently for class declarations.
            logger.trace("Found aType with no inferenceAnnotations. Returning null");
            return null;
        } else if (aType.getAnnotations().size() > 1) {
            // Canary for bugs with VarAnnots
            ErrorReporter.errorAbort("Found type in inference with the wrong number of annotations. Should always have 0 or 1: " + aType);
            return null; // dead
        } else {
            return new InferenceValue((InferenceAnalysis) analysis, aType);
        }
    }

    /**
     * @returns InferenceStore, so that InferenceStore is used by inference dataflow.
     */
    @Override
    public InferenceStore createEmptyStore(boolean sequentialSemantics) {
        return new InferenceStore(this, sequentialSemantics);
    }

    /**
     * @returns InferenceStore, so that InferenceStore is used by inference dataflow.
     */
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