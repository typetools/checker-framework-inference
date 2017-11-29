package checkers.inference.solver.backend.maxsat;

import java.util.Collection;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.AbstractSolverFactory;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public class MaxSatSolverFactory extends AbstractSolverFactory<MaxSatFormatTranslator> {

    @Override
    public Solver<?> createSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {
        MaxSatFormatTranslator formatTranslator = createFormatTranslator(lattice);
        return new MaxSatSolver(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }

    @Override
    public MaxSatFormatTranslator createFormatTranslator(Lattice lattice) {
        return new MaxSatFormatTranslator(lattice);
    }

}
