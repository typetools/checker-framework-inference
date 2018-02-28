package checkers.inference;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.Pair;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;

import checkers.inference.dataflow.InferenceAnalysis;
import checkers.inference.model.ConstraintManager;

import com.sun.source.tree.Tree;

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

    public InferenceAnnotatedTypeFactory createInferenceATF(InferenceChecker inferenceChecker,
            InferrableChecker realChecker, BaseAnnotatedTypeFactory realTypeFactory,
            SlotManager slotManager, ConstraintManager constraintManager);

    // Instantiate a visitor based on parameters
    InferenceVisitor<?, ?> createVisitor(InferenceChecker checker, BaseAnnotatedTypeFactory factory, boolean infer);

    /**
     * Should inference generate variables and constraints for
     * viewpoint adaption when accessing instance members.
     */
    boolean withCombineConstraints();

    CFTransfer createInferenceTransferFunction(InferenceAnalysis analysis);

    CFAnalysis createInferenceAnalysis(
            InferenceChecker checker,
            GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
            List<Pair<VariableElement, CFValue>> fieldValues,
            SlotManager slotManager, ConstraintManager constraintManager,
            InferrableChecker realChecker);

    /**
     * Should this node be treated as having a constant value.
     *
     * If true, the underlying ATF will be used to look up the type of the node
     * and an equality constraint will be generated for between the VarAnnot
     * and the annotation from the underlying ATF.
     *
     * @param node the node
     * @return true if the node should be treated as constant
     */
    boolean isConstant(Tree node);


    /**
     * @return true whether or not the SlotManager should try to maintain a store of
     * AnnotationMirror -> ConstantSlot in order to avoid creating multiple constants
     * for the same annotation. For parameterized qualifiers this should return false.
     */
    boolean shouldStoreConstantSlots();

    /**
     * should the inference annotations of main modifier of local variables also insert
     * into source code
     *
     * Generally a checker would not want insert annotations of main modifier of local vars
     * because they can inferred by flow refinement. For some specific checkers, e.g. Ontology,
     * they may not want ignore those information.
     *
     * @return true if should insert annotations of main modifier of local variables
     */
    boolean isInsertMainModOfLocalVar();

    /**
     * If the checker inserts alias annotations (any annotation that isn't part of the supported
     * qualifiers set) into source code, then the class literals for these alias annotations should
     * be returned in an override of this method.
     *
     * For example, in Units Checker, it is preferred to insert {@code @m}, an alias annotation,
     * into source code instead of the corresponding internal representation annotation
     * {@code @UnitsInternal(...)} as the alias annotation is easier to understand for users.
     *
     * The default implementation of this method in {@
     * BaseInferrableChecker#additionalAnnotationsForJaifHeaderInsertion()} returns an empty set.
     *
     * @return a set of any additional annotations that need to be inserted as annotation headers
     *         into Jaif files.
     */
    Set<Class<? extends Annotation>> additionalAnnotationsForJaifHeaderInsertion();
}