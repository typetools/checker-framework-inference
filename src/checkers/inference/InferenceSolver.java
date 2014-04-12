package checkers.inference;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;

public interface InferenceSolver {

    /**
     * Solve the constraints and return a mapping of slot id to an resulting
     * AnnotationMirror.
     *
     * @param solverArgs String key value pairs to configure the solver
     * @param slots List of all slots used in inference
     * @param constraints List of Constraints to be satisfied
     * @param qualHierarchy Target QualifierHierarchy
     * @return
     */
    Map<Integer, AnnotationMirror> solve(Map<String, String> configuration,
            List<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment);
}
