package checkers.inference.solver.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.DefaultInferenceSolution;
import checkers.inference.InferenceSolution;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.SolverEngine.SolverEngineArg;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.backend.SolverFactory;
import checkers.inference.solver.constraintgraph.ConstraintGraph;
import checkers.inference.solver.constraintgraph.GraphBuilder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.PrintUtils;
import checkers.inference.solver.util.SolverArg;
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.StatisticRecorder;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * GraphSolvingStrategy solves a given set of constraints by a divide-and-conquer way:
 *
 * 1. Build a {@link ConstraintGraph} based on the given set of constraints.
 * 2. Divide the constraint graph to multiple sub-graphs.
 * 3. For each sub-graphs, assign an underlying solver to solve it.
 * 4. Merge solutions of sub-graphs to get the final solution.
 *
 * This solving strategy is useful when solving constraints for a type system with a huge number of qualifers.
 * Normal plain solving strategy meet exponentially increased solving time in this case.
 */
public class GraphSolvingStrategy extends AbstractSolvingStrategy implements SolvingStrategy {

    enum GraphSolveStrategyArg implements SolverArg {
        solveInParallel;
    }

    public GraphSolvingStrategy(SolverFactory solverFactory) {
        super(solverFactory);
    }

    @Override
    public InferenceSolution solve(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Lattice lattice) {

        //TODO: Remove the coupling of using SolverEngineArg.
        final boolean solveInParallel = !"lingeling".equals(solverEnvironment.getArg(SolverEngineArg.solver))
                && solverEnvironment.getBoolArg(GraphSolveStrategyArg.solveInParallel);

        // Build graph
        final long graphBuildingStart = System.currentTimeMillis();
        ConstraintGraph constraintGraph = generateGraph(slots, constraints, solverEnvironment.processingEnvironment);
        final long graphBuildingEnd = System.currentTimeMillis();

        // Separate constraint graph, and assign each separated sub-graph to a underlying solver to solve.
        List<Solver<?>> separatedGraphSolvers = separateGraph(solverEnvironment, constraintGraph,
                slots, constraints, lattice);

        // Solving.
        List<Map<Integer, AnnotationMirror>> inferenceSolutionMaps = new LinkedList<Map<Integer, AnnotationMirror>>();

        if (separatedGraphSolvers.size() > 0) {
            if (solveInParallel) {
                try {
                    inferenceSolutionMaps = solveInparallel(separatedGraphSolvers);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            } else {
                inferenceSolutionMaps = solveInSequential(separatedGraphSolvers);
            }
        }
       

        // Merge solutions.
        InferenceSolution solution = mergeSolution(inferenceSolutionMaps);
       
        //TODO: Refactor way of recording Statistics.
        StatisticRecorder.record(StatisticKey.GRAPH_GENERATION_TIME, (graphBuildingEnd - graphBuildingStart));
        StatisticRecorder.record(StatisticKey.GRAPH_SIZE, (long) constraintGraph.getIndependentPath().size());

        return solution;
    }

    protected ConstraintGraph generateGraph(Collection<Slot> slots, Collection<Constraint> constraints,
            ProcessingEnvironment processingEnvironment) {
        GraphBuilder graphBuilder = new GraphBuilder(slots, constraints);
        ConstraintGraph constraintGraph = graphBuilder.buildGraph();
        return constraintGraph;
    }

    /**
     * Separate constraint graph, and build an underlying solver for each separated sub-graph.
     *
     * Sub-class may customize their own way of separating the constraint graph by overriding this method.
     *
     * @return a list of underlying solvers, each of them responsible for solving a separated
     * sub-graph from the given constraint graph.
     */
    protected List<Solver<?>> separateGraph(SolverEnvironment solverEnvironment, ConstraintGraph constraintGraph,
            Collection<Slot> slots, Collection<Constraint> constraints, Lattice lattice) {
        List<Solver<?>> separatedGraphSovlers = new ArrayList<>();

        for (Set<Constraint> independentConstraints : constraintGraph.getIndependentPath()) {
            separatedGraphSovlers.add(solverFactory.createSolver(solverEnvironment, slots, independentConstraints, lattice));
        }

        return separatedGraphSovlers;
    }

    /**
     * This method is called if user wants to call all underlying solvers in parallel.
     * 
     * @param underlyingSolvers
     * @return A list of Map that contains solutions from all underlying solvers.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    protected List<Map<Integer, AnnotationMirror>> solveInparallel(List<Solver<?>> underlyingSolvers)
            throws InterruptedException, ExecutionException {

        ExecutorService service = Executors.newFixedThreadPool(30);
        List<Future<Map<Integer, AnnotationMirror>>> futures = new ArrayList<Future<Map<Integer, AnnotationMirror>>>();

        long solvingStart = System.currentTimeMillis();
        for (final Solver<?> underlyingSolver : underlyingSolvers) {
            Callable<Map<Integer, AnnotationMirror>> callable = new Callable<Map<Integer, AnnotationMirror>>() {
                @Override
                public Map<Integer, AnnotationMirror> call() throws Exception {
                    return underlyingSolver.solve();
                }
            };
            futures.add(service.submit(callable));
        }
        service.shutdown();

        List<Map<Integer, AnnotationMirror>> solutions = new ArrayList<>();

        for (Future<Map<Integer, AnnotationMirror>> future : futures) {
            solutions.add(future.get());
        }
        long solvingEnd = System.currentTimeMillis();

        //TODO: Refactor way of recording statistic.
        StatisticRecorder.record(StatisticKey.OVERALL_PARALLEL_SOLVING_TIME, (solvingEnd - solvingStart));
        return solutions;
    }

    /**
     * This method is called if user wants to call all underlying solvers in sequence.
     * 
     * @param underlyingSolvers
     * @return A list of Map that contains solutions from all underlying solvers.
     */
    protected List<Map<Integer, AnnotationMirror>> solveInSequential(List<Solver<?>> underlyingSolvers) {

        List<Map<Integer, AnnotationMirror>> solutions = new ArrayList<>();

        long solvingStart = System.currentTimeMillis();
        for (final Solver<?> underlyingSolver : underlyingSolvers) {
            solutions.add(underlyingSolver.solve());
        }
        long solvingEnd = System.currentTimeMillis();
        //TODO: Refactor way of recording statistic.
        StatisticRecorder.record(StatisticKey.OVERALL_SEQUENTIAL_SOLVING_TIME, (solvingEnd - solvingStart));
        return solutions;
    }

    /**
     * This method merges all solutions from all underlying solvers.
     * 
     * @param inferenceSolutionMaps
     * @return an InferenceSolution for the given slots/constraints
     */
    protected InferenceSolution mergeSolution(List<Map<Integer, AnnotationMirror>> inferenceSolutionMaps) {

        Map<Integer, AnnotationMirror> result = new HashMap<>();

        for (Map<Integer, AnnotationMirror> inferenceSolutionMap : inferenceSolutionMaps) {
            result.putAll(inferenceSolutionMap);
        }
        PrintUtils.printResult(result);
        StatisticRecorder.record(StatisticKey.ANNOTATOIN_SIZE, (long) result.size());
        return new DefaultInferenceSolution(result);
    }
}
