package checkers.inference.dataflow;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.SystemUtil;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;

import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstraintManager;

/**
 * InferenceAnalysis tweaks dataflow for Checker-Framework-Inference.
 *
 * Checker-Framework-Inference's dataflow is primarily concerned with the creation
 * and maintenance of RefinementVariableSlots. (See RefinementVariableSlots).
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

    private static final Logger logger = Logger.getLogger(InferenceAnalysis.class.getName());
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
    public CFValue defaultCreateAbstractValue(CFAbstractAnalysis<CFValue, ?, ?> analysis,
                                              Set<AnnotationMirror> annos,
                                              TypeMirror underlyingType) {

        if (annos.size() == 0 && underlyingType.getKind() != TypeKind.TYPEVAR) {
            // This happens for currently for class declarations.
            logger.fine("Found type with no inferenceAnnotations. Returning null. Type found: "
                    + underlyingType.toString());
            return null;
        } else if (annos.size() > 2) {
            // Canary for bugs with VarAnnots
            // Note: You can have 1 annotation if a primary annotation in the real type system is
            // present for a type variable use or wildcard
            throw new BugInCF("Found type in inference with the wrong number of "
                    + "annotations. Should always have 0, 1, or 2: " + SystemUtil.join(", ",
                    annos));
        } else {
            return new InferenceValue((InferenceAnalysis) analysis, annos, underlyingType);
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
