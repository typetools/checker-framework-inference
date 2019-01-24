package sparta.checkers.propagation;

/**
 * Solver class for solving @Source annotations.
 *
 * <p>This is its own class so that is can be referenced from the command line.
 */
public class IFlowSourceSolver extends IFlowSolver {

    @Override
    public boolean isSinkSolver() {
        return false;
    }
}
