package checkers.inference.solver.strategy;

import java.util.Collection;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.DefaultInferenceSolution;
import checkers.inference.InferenceSolution;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.backend.SolverFactory;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public class PlainSolvingStrategy extends AbstractSolvingStrategy implements SolvingStrategy {

    public PlainSolvingStrategy(SolverFactory solverFactory) {
        super(solverFactory);
    }

    @Override
    public InferenceSolution solve(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {

        Solver<?> underlyingSolver = solverFactory.createSolver(solverEnvironment, slots, constraints, lattice);

        Map<Integer, AnnotationMirror> result = underlyingSolver.solve();

        return new DefaultInferenceSolution(result);
    }


}
