package checkers.inference.solver.backend.logiql;

import java.util.Collection;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.AbstractSolverFactory;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public class LogiQLSolverFactory extends AbstractSolverFactory<LogiQLFormatTranslator> {

    @Override
    public Solver<?> createSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {
        LogiQLFormatTranslator formatTranslator = createFormatTranslator(lattice);
        return new LogiQLSolver(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }

    @Override
    public LogiQLFormatTranslator createFormatTranslator(Lattice lattice) {
        return new LogiQLFormatTranslator(lattice);
    }

}
