package checkers.inference;

import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

/**
 * Interface for the annotatedTypeFactory that wish to let
 * InferenceAnnotatedTypeFactory use the TreeAnnotator from current type system.
 */

public interface InferrableAnnotatedTypeFactory {

    /**
     * The default TreeAnnotator used by InferenceAnnotatedTypeFactory is
     * InferenceTreeAnnotator. This method could be called if the TreeAnnotator
     * defined in current type system is needed.
     * 
     * @see checkers.inference.InferenceAnnotatedTypeFactory#createTreeAnnotator()
     * 
     * @return the TreeAnnotator, a subtype of InferenceTreeAnnotator, that
     *         performs the special behavior for current type system from real
     *         type annotatedTypeFactory.
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
