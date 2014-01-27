package checkers.inference;

import java.util.List;

import javacutils.Pair;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;

import checkers.basetype.BaseAnnotatedTypeFactory;
import checkers.flow.CFAnalysis;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;
import checkers.inference.dataflow.InferenceAnalysis;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.GenericAnnotatedTypeFactory;

/**
 * Interface for all checkers that wish to be used with Checker-Framework-Inference
 *
 * This interface allows a checker to configure is inference behavior.
 *
 * Some methods are from BaseTypeChecker as convenience to Inference classes so they do not need to have multiple
 * references to the same class.
 *
 * @author mcarthur
 *
 */

public interface InferrableChecker {

    // Initialize the underlying checker
    void init(ProcessingEnvironment processingEnv);
    void initChecker();

    // Instantiate the real type factory
    BaseAnnotatedTypeFactory createRealTypeFactory();

    // Instantiate a visitor based on parameters
    InferenceVisitor createVisitor(InferenceChecker checker, BaseAnnotatedTypeFactory factory, boolean infer);

    /**
     * Should inference generate variables and constraints for
     * viewpoint adaption when accessing instance members.
     */
    boolean withCombineConstraints();

    /**
     * Prevents a Variable from being created by the InferenceAnnotatedTypeSystem
     * Instead, a ConstantSlot is created with value returned by the underlying
     * type system.
     *
     *
     * @param typeMirror type to check
     * @return Should the type mirror be treated as having a constant value
     */
    boolean isConstant(AnnotatedTypeMirror typeMirror);

    CFTransfer createInferenceTransferFunction(InferenceAnalysis analysis);

    CFAnalysis createInferenceAnalysis(
            InferenceChecker checker,
            GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
            List<Pair<VariableElement, CFValue>> fieldValues,
            SlotManager slotManager, ConstraintManager constraintManager,
            InferrableChecker realChecker);

}