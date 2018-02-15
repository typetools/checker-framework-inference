package checkers.inference;

import checkers.inference.model.Constraint;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.Map;

/**
 * Represents the result of inference.
 */
public interface InferenceResult {

    /**
     * Indicates if inference has solution or not.
     *
     * @return true if inference result contains valid solutions for variable IDs.
     * Returns false if underlying solver fails to give solution for {@link Constraint}s.
     *
     * @see #getSolutions()
     * @see #containsSolutionForVariable(int)
     * @see #getSolutionForVariable(int)
     * @see #getUnsatisfiableConstraints()
     */
    boolean hasSolution();

    /**
     * Gets inference solutions from the result.
     *
     * @return inference solutions. Null if {{@link #hasSolution()}} is false.
     *
     * @see #hasSolution()
     */
    Map<Integer, AnnotationMirror> getSolutions();

    /**
     * A method to check if there is solution for a particular variable ID or not.
     *
     * @param varId id of a {@link checkers.inference.model.VariableSlot VariableSlot}
     * @return true iff {@link #hasSolution()} returns true and internal inferred
     * result(implementation detail) contains solution for {@code varId}
     *
     * @see #hasSolution()
     * @see #getSolutionForVariable(int)
     */
    boolean containsSolutionForVariable(int varId);

    /**
     * A method to get the inferred solution for the given variable ID.
     *
     * @param varId id of a {@link checkers.inference.model.VariableSlot VariableSlot}
     * @return non-null solution iff {@link #hasSolution()} returns true and internal
     * inferred result(implementation detail) contains solution for {@code varId}
     *
     * @see #hasSolution()
     * @see #containsSolutionForVariable(int)
     */
    AnnotationMirror getSolutionForVariable(int varId);

    /**
     * Access method to get set of {@link Constraint}s that are not solvable together.
     *
     * Should be called only if {@link #hasSolution()} returns false. In this case, if
     * an empty collection is returned, it means the underlying solver doesn't support
     * explaning unsolvable reason.
     *
     * @return set of {@code Constraint}s that are not solvable together
     *
     * @see #hasSolution()
     */
    Collection<Constraint> getUnsatisfiableConstraints();
}

