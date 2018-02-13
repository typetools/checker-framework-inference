package checkers.inference;

import checkers.inference.model.Constraint;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Default implementation of {@link InferenceResult}.
 *
 * This class'es implementation follows this pattern:
 * No solution == {@link #varIdToAnnotation} is null
 * Has solution == empty {@link #varIdToAnnotation solution} or non-empty {@link #varIdToAnnotation solution}
 *
 * @see {@link #hasSolution()}
 */
public class DefaultInferenceResult implements InferenceResult {

    /**
     * A map from variable Id of {@link checkers.inference.model.VariableSlot VariableSlot}
     * to {@link AnnotationMirror}.
     *
     * No solution should set this field to null. Otherwise, if the map is empty, it means
     * empty solution(solution with no variable IDs). This happens for trivial testcase that
     * doesn't need to insert annotations to source code.
     *
     * @see #hasSolution()
     */
    protected final Map<Integer, AnnotationMirror> varIdToAnnotation;

    /**
     * Set of {@link Constraint}s that caused solver not being able to give solutions.
     *
     * If {@link #varIdToAnnotation} is null, there is no solution. This field then needs to
     * passed in a non-null non-empty set as explanation. But if a solver backend doesn't support
     * explanation feature, an empty set is allowed to be passed in, so that caller knows the backend
     * can not explain unsolvable reason.
     * If {@link #varIdToAnnotation} is non-null, solution exists. In this case, this field
     * is not accessed(otherwise, throw {@code UnsupportedOperationException}), so it does't really
     * matter what to be passed in here. But conservatively, an empty set is preferred. There is a
     * dedicated constructor {@link DefaultInferenceResult(Map<Integer, AnnotationMirror>)} for this
     * situation. For the best pratice, client should always call this constructor for cases with solutions.
     */
    protected final Collection<Constraint> unsatisfiableConstraints;

    /**
     * No-arg constructor.
     *
     * Should be called in situations:
     * 1) Try to create empty solution
     * 2) Subclass calls this super constructor to begin with an empty map. Then subclass
     * has its logic to adding solutions to the mapping {@link #varIdToAnnotation}. The existing two
     * subclasses are: {@link dataflow.solvers.classic.DataflowResult} and {@link sparta.checkers.sat.IFlowResult}.
     */
    public DefaultInferenceResult() {
        this(new HashMap<>());
    }

    /**
     * One-arg constructor that accepts {@code varIdToAnnotation}.
     *
     * Should be called when inference has solutions(either empty or non-empty, but never
     * null). This case should be the most frequent.
     *
     * @param varIdToAnnotation mapping from variable ID to inferred solution {@code AnnotationMirror}
     */
    public DefaultInferenceResult(Map<Integer, AnnotationMirror> varIdToAnnotation) {
        this(varIdToAnnotation, new HashSet<>());
    }

    /**
     * One-arg constructor that accepts {@code unsatisfiableConstraints}.
     *
     * Should be called when inference failed to give solutions.
     *
     * @param unsatisfiableConstraints non-null set of unsolable constraints. If a solver backend doesn't
     *                                 support explaining, empty set should be passed.
     */
    public DefaultInferenceResult(Collection<Constraint> unsatisfiableConstraints) {
        this(null, unsatisfiableConstraints);
    }

    private DefaultInferenceResult(Map<Integer, AnnotationMirror> varIdToAnnotation,
                                  Collection<Constraint> unsatisfiableConstraints) {
        if (unsatisfiableConstraints == null) {
            throw new IllegalArgumentException("unsatisfiableConstraints should never be null!");
        }
        this.varIdToAnnotation = varIdToAnnotation;
        this.unsatisfiableConstraints = unsatisfiableConstraints;
    }

    @Override
    public boolean hasSolution() {
        return varIdToAnnotation != null;
    }

    @Override
    public Map<Integer, AnnotationMirror> getSolutions() {
        if (!hasSolution()) {
            return null;
        }
        return varIdToAnnotation;
    }

    @Override
    public boolean containsSolutionForVariable(int varId) {
        if (!hasSolution()) {
            return false;
        }
        return varIdToAnnotation.containsKey(varId);
    }

    @Override
    public AnnotationMirror getSolutionForVariable(int varId) {
        if (!hasSolution()) {
            return null;
        }
        return varIdToAnnotation.get(varId);
    }

    @Override
    public Collection<Constraint> getUnsatisfiableConstraints() {
        if (hasSolution()) {
            throw new UnsupportedOperationException(
                    "There is solution, calling `getUnsatisfiableConstraints()` is forbidden!");
        }
        return unsatisfiableConstraints;
    }
}
