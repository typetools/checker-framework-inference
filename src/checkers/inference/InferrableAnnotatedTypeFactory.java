package checkers.inference;

import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

/**
 * An {@code AnnotatedTypeFactory} for use with
 * {@code checker-framework-inference} should implement this
 * interface, to allow adaptation of constraint variable introduction
 * rules. If no adaptations are necessary, this interface doesn't need
 * to be implemented.
 *
 * Inference always uses
 * {@link checkers.inference.InferenceAnnotatedTypeFactory} to
 * introduce constraint variables.
 * If the "real" AnnotatedTypeFactory of the type system implements
 * this interface, the methods of this interface will be used to adapt
 * the behavior of the {@code InferenceAnnotatedTypeFactory}.
 *
 * @see checkers.inference.InferenceAnnotatedTypeFactory
 * @see org.checkerframework.framework.type.AnnotatedTypeFactory
 * @see checkers.inference.InferrableChecker
 */
public interface InferrableAnnotatedTypeFactory {

    /**
     * Return the {@code TreeAnnotator} that should be used for
     * inference.
     * The type system developer is free to use this {@code
     * TreeAnnotator} to either add annotations from the "real" type
     * system or to create constraint variables.
     *
     * @return The {@code TreeAnnotator} for inference.
     *
     * @see checkers.inference.InferenceAnnotatedTypeFactory#createTreeAnnotator()
     * @see checkers.inference.InferenceTreeAnnotator#InferenceTreeAnnotator(InferenceAnnotatedTypeFactory,
     *      InferrableChecker, AnnotatedTypeFactory, VariableAnnotator,
     *      SlotManager)
     */
    TreeAnnotator getInferenceTreeAnnotator(
            InferenceAnnotatedTypeFactory atypeFactory,
            InferrableChecker realChecker,
            VariableAnnotator variableAnnotator, SlotManager slotManager);
}
