package checkers.inference;

import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

/**
 * Interface for the AnnotatedTypeFactory that wishes to be used with
 * checker-framework-inference. Users could use this interface to configure
 * inference behavior of current AnnotatedTypeFactory.
 * {@link org.checkerframework.framework.type.AnnotatedTypeFactory}
 */

public interface InferrableAnnotatedTypeFactory {

    /**
     * Return the TreeAnnotator defined in current type system. If this method
     * is not called, InferenceTreeAnnotator will be generated and used by
     * InferenceAnnotatedTypeFactory.
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
