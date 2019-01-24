package sparta.checkers.propagation;

/**
 * Solver class for solving @Sink annotations.
 *
 * <p>This is its own class so that is can be referenced from the command line.
 */
public class IFlowSinkSolver extends IFlowSolver {

    @Override
    public boolean isSinkSolver() {
        return true;
    }
}
