package checkers.inference;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;

/**
 * Interface for all annotatedTypeFactory that wish to be used with
 * Checker-Framework-Inference
 */

public interface InferrableAnnotatedTypeFactory {

    /**
     * return the TreeAnnotator from real type annotatedTypeFactory
     * 
     */
    TreeAnnotator getRealInferenceTreeAnnotator(
            InferenceAnnotatedTypeFactory atypeFactory,
            InferrableChecker realChecker,
            AnnotatedTypeFactory realAnnotatedTypeFactory,
            VariableAnnotator variableAnnotator, SlotManager slotManager);
}
