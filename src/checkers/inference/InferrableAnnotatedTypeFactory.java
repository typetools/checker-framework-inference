package checkers.inference;

import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

/**
 * Interface for indicating that the current the annotatedTypeFactory wish to be
 * used by checker-framework-inference. This interface allows a
 * annotatedTypeFactory to configure its own inference behavior.
 */

public interface InferrableAnnotatedTypeFactory {

    /**
     * This method could be called if the TreeAnnotator defined in current type
     * system is needed. Otherwise, InferenceTreeAnnotator will be generated and
     * used by InferenceAnnotatedTypeFactory.
     *
     * @see checkers.inference.InferenceAnnotatedTypeFactory#createTreeAnnotator()
     *
     * @return TreeAnnotator, it could be subtype of InferenceTreeAnnotator,
     *         which performs the special behavior for current type system, or a
     *         ListTreeAnnotator, such that ImplicitsTreeAnnotator could be
     *         contained.
     *
     * @see checkers.inference.InferenceTreeAnnotator#InferenceTreeAnnotator(InferenceAnnotatedTypeFactory,
     *      InferrableChecker, AnnotatedTypeFactory, VariableAnnotator,
     *      SlotManager)
     */
    TreeAnnotator getInferenceTreeAnnotator(
            InferenceAnnotatedTypeFactory atypeFactory,
            InferrableChecker realChecker,
            VariableAnnotator variableAnnotator, SlotManager slotManager);
}
