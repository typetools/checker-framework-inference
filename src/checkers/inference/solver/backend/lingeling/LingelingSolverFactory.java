package checkers.inference.solver.backend.lingeling;

import java.util.Collection;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.AbstractSolverFactory;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public class LingelingSolverFactory extends AbstractSolverFactory<MaxSatFormatTranslator> {

    @Override
    public Solver<?> createSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {
        MaxSatFormatTranslator formatTranslator = createFormatTranslator(lattice);
        return new LingelingSolver(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }

    @Override
    protected MaxSatFormatTranslator createFormatTranslator(Lattice lattice) {
           return new MaxSatFormatTranslator(lattice);
    }

}
