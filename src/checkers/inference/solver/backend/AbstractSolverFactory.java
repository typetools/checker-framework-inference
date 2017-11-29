package checkers.inference.solver.backend;

import checkers.inference.solver.frontend.Lattice;

/**
 * Abstract base class for all concrete {@link SolverFactory}.
 *
 * This class define an abstract method {@link #createFormatTranslator(Lattice)},
 * in order to let subclass be able to provide customized format translators.
 * @param <T>
 */
public abstract class AbstractSolverFactory<T extends FormatTranslator<?, ?, ?>> implements SolverFactory {

    /**
     * Create a format translator coordinate with the created solver by this factory.
     *
     * @param lattice the target lattice
     * @return a format translator, responsible for decoding/encoding for the created solver.
     */
    abstract protected T createFormatTranslator(Lattice lattice);
}
