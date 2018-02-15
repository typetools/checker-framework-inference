package checkers.inference.solver.strategy;

import java.util.Collection;

import checkers.inference.InferenceResult;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.SolverEngine;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

/**
 * Define a strategy on solving constriants.
 *
 * Note: subclasses within the Solver Framework should follow naming conventions,
 * in order to let {@code SolverEngine} be able to reflectively load subclass instance.
 *
 * Naming convention is:
 * Package: subclasses should be created within current package checkers.inference.solver.strategy.
 * Class name: [StrategyName]SolvingStrategy.
 *
 * E.g. For graph solving strategy, the class name should be: GraphSolvingStrategy.
 *
 * @see SolverEngine#createSolvingStrategy()
 */
public interface SolvingStrategy {

    /**
     * Solve the constraints by the solving strategy defined in this method.
     *
     */
    InferenceResult solve(SolverEnvironment solverEnvironment, Collection<Slot> slots,
                          Collection<Constraint> constraints, Lattice lattice);
}
