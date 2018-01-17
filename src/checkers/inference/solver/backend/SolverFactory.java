package checkers.inference.solver.backend;

import java.util.Collection;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.SolverEngine;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

/**
 * Factory class for creating an underlying {@link Solver}.
 *
 * Note: subclass of this interface should have a zero parameter
 * constructor, and should follow the naming convention to let {@code SolverEngine}
 * reflectively load the subclass instance.
 *
 * Naming convention of solver factory for underlying solvers:
 *
 * Package name should be: checkers.inference.solver.backend.[(all lower cases)underlying solver name]
 * Under this package, create a subclass named: [underlying solver name]SolverFactory.
 *
 * E.g. For MaxSat solver:
 *
 * Package name: checkers.inference.solver.backend.maxsat
 * Class name: MaxSatSolverFactory
 *
 * @see SolverEngine#createSolverFactory()
 */
public interface SolverFactory {

    Solver<?> createSolver(SolverEnvironment solverOptions,
            Collection<Slot> slots, Collection<Constraint> constraints, Lattice lattice);
}
