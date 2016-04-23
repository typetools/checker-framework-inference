package checkers.inference;

import javax.lang.model.element.AnnotationMirror;

/**
 * Returned by InferenceSolvers, InferenceSolution represents the result of
 * inference.
 */
public interface InferenceSolution {
    /**
     * Was a solution inferred for the given variable ID? Equivalent to
     * getAnnotation(id) != null.
     */
    boolean doesVariableExist(int varId);

    /**
     * Get the inferred solution for the given variable ID. Will return null if
     * doesVariableExist(id) is false.
     */
    AnnotationMirror getAnnotation(int varId);
}

