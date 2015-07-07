package checkers.inference;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * Returned by InferenceSolvers, InferenceSolution represents the result of inference
 */
public interface InferenceSolution {
    /**
     * @return a mapping between variableId and the annotation in the real type system that should
     * be located in the corresponding variable's location
     */
    Map<Integer, AnnotationMirror> getVarIdToAnnotation();

    /**
     * Some variables may or may not exist, this method indicates whether they should or not.
     * @return A mapping from variableId -> should variable with this id exist
     */
    Map<Integer, Boolean> getIdToExistance();

    /**
     * Convenience method that is equivalent to getIdToExistance().get(varId);
     */
    boolean doesVariableExist(int varId);
    /*
     * Convenience method that is equivalent to getVarIdToAnnotation().get(varId);
     */
    AnnotationMirror getAnnotation(int varId);
}

