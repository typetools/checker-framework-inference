package checkers.inference;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.types.QualifierHierarchy;

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
    Map<Integer, AnnotationMirror> solve(
            Map<String, String> solverArgs,
            List<Slot> slots,
            List<Constraint> constraints,
            QualifierHierarchy qualHierarchy);
}
