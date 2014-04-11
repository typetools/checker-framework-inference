package sparta.checkers;

/**
 * Solver class for solving @Source annotations.
 *
 * This is its own class so that is can be referenced from the command line.
 *
 */
public class SpartaSourceSolver extends SpartaSolver {

    @Override
    public boolean isSinkSolver() {
        return false;
    }
}
