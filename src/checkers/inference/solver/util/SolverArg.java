package checkers.inference.solver.util;

/**
 * Command line solver argument definition.
 *
 * Subclass of this interface should be an enum class that
 * grouping a set of related solver arguments.
 *
 * {@link SolverEnvironment} provides methods of taking SolverArg
 * and parsing them to String value or boolean values.
 */
public interface SolverArg {
    /**
     * The string value of the argument name.
     * This method is intentionally named as same as the {@link Enum#name()}
     * method, so that concrete enum class only take car of defining
     * arguments, and could use the default {@link Enum#name()} as the
     * implementation of this method.
     * @return
     */
    String name();
}
