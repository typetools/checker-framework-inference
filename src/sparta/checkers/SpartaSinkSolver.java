package sparta.checkers;

/**
 * Solver class for solving @Sink annotations.
 *
 * This is its own class so that is can be referenced from the command line.
 *
 */
public class SpartaSinkSolver extends SpartaSolver {

    @Override
    public boolean isSinkSolver() {
        return true;
    }
}
