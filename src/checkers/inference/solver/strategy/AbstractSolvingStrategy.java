package checkers.inference.solver.strategy;

import checkers.inference.solver.backend.SolverFactory;

/**
 * Abstract base class for all concrete {@link SolvingStrategy} implementation. *
 */
public abstract class AbstractSolvingStrategy implements SolvingStrategy {

    /**
     * The solver factory used to create underlying solver that responsible for
     * solving constraints in this solving strategy.
     */
    protected final SolverFactory solverFactory;

    public AbstractSolvingStrategy(SolverFactory solverFactory) {
        this.solverFactory = solverFactory;
    }

}
