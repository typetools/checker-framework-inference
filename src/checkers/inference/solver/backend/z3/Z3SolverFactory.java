package checkers.inference.solver.backend.z3;

import java.util.Collection;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.AbstractSolverFactory;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public abstract class Z3SolverFactory extends AbstractSolverFactory<Z3BitVectorFormatTranslator> {

    @Override
    public Solver<?> createSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {
        Z3BitVectorFormatTranslator formatTranslator = createFormatTranslator(lattice);
        return new Z3Solver(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }
}
