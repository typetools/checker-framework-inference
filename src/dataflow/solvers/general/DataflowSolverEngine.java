package dataflow.solvers.general;

import checkers.inference.InferenceMain;
import checkers.inference.solver.SolverEngine;
import checkers.inference.solver.backend.SolverFactory;
import checkers.inference.solver.strategy.GraphSolvingStrategy;
import checkers.inference.solver.strategy.SolvingStrategy;
import checkers.inference.solver.util.NameUtils;

/**
 * DataflowGeneralSolver is the solver for dataflow type system. It encode
 * dataflow type hierarchy as two qualifiers type system.
 * 
 * @author jianchu
 *
 */
public class DataflowSolverEngine extends SolverEngine {

    @Override
    protected SolvingStrategy createSolvingStrategy(SolverFactory solverFactory) {
        return new DataflowGraphSolvingStrategy(solverFactory);
    }

    @Override
    protected void sanitizeSolverEngineArgs() {
        if (!NameUtils.getStrategyName(GraphSolvingStrategy.class).equals(strategyName)) {
            InferenceMain.getInstance().logger.warning("Dataflow type system must use graph solve strategy."
                    + "Change strategy from " + strategyName + " to graph.");
            strategyName = NameUtils.getStrategyName(GraphSolvingStrategy.class);
        }
    }
}
