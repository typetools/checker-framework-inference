package checkers.inference.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

import checkers.inference.InferenceMain;
import checkers.inference.util.ConstraintVerifier;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.DefaultInferenceSolution;
import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.SolverAdapter;
import checkers.inference.solver.backend.SolverType;
import checkers.inference.solver.backend.FormatTranslator;
import checkers.inference.solver.constraintgraph.ConstraintGraph;
import checkers.inference.solver.constraintgraph.GraphBuilder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.LatticeBuilder;
import checkers.inference.solver.util.Constants;
import checkers.inference.solver.util.Constants.SolverArg;
import checkers.inference.solver.util.Constants.SlotType;
import checkers.inference.solver.util.PrintUtils;
import checkers.inference.solver.util.StatisticRecorder;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * GeneralSolver is the entry point of general solver framework, and it is also
 * the front end of whole solver system. GeneralSolver configures command line
 * arguments, creates corresponding back end(s) and serializer, invokes the back
 * end(s) and returns the solution.
 * 
 * @author jianchu
 *
 */

public class SolverEngine implements InferenceSolver {

    protected SolverType solverType;
    protected boolean useGraph;
    protected boolean solveInParallel;
    protected boolean collectStatistic;
    protected Lattice lattice;
    protected ConstraintGraph constraintGraph;
    protected SolverAdapter<?> underlyingSolver;

    // Timing variables:
    private long solvingStart;
    private long solvingEnd;

    @Override
    public InferenceSolution solve(Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        InferenceSolution solution = null;

        configureSolverArgs(configuration);
        configureLattice(qualHierarchy, slots);

        final ConstraintVerifier verifier = InferenceMain.getInstance().getConstraintManager().getConstraintVerifier();
        FormatTranslator<?, ?, ?> formatTranslator = createFormatTranslator(solverType, lattice, verifier);

        if (useGraph) {
            final long graphBuildingStart = System.currentTimeMillis();
            constraintGraph = generateGraph(slots, constraints, processingEnvironment);
            final long graphBuildingEnd = System.currentTimeMillis();
            StatisticRecorder.record(StatisticKey.GRAPH_GENERATION_TIME, (graphBuildingEnd - graphBuildingStart));
            solution = graphSolve(constraintGraph, configuration, slots, constraints, qualHierarchy,
                    processingEnvironment, formatTranslator);
        } else {
            underlyingSolver = createSolverAdapter(solverType, configuration, slots, constraints,
                    processingEnvironment, lattice, formatTranslator);
            solution = solve();
        }

        if (solution == null) {
            // Solution should never be null.
            ErrorReporter.errorAbort("Null solution detected!");
        }

        if (collectStatistic) {
            Map<String, Integer> modelRecord = recordSlotConstraintSize(slots, constraints);
            PrintUtils.printStatistic(StatisticRecorder.getStatistic(), modelRecord, solverType,
                    useGraph, solveInParallel);
            PrintUtils.writeStatistic(StatisticRecorder.getStatistic(), modelRecord, solverType,
                    useGraph, solveInParallel);
        }
        return solution;
    }

    /**
     * This method configures following arguments: backEndType, useGraph,
     * solveInParallel, and collectStatistic
     * 
     * @param configuration
     */
    private void configureSolverArgs(final Map<String, String> configuration) {

        final String solverName = configuration.get(SolverArg.solver.name());
        final String useGraph = configuration.get(SolverArg.useGraph.name());
        final String solveInParallel = configuration.get(SolverArg.solveInParallel.name());
        final String collectStatistic = configuration.get(SolverArg.collectStatistic.name());

        solverType = solverName == null ? SolverType.MAXSAT : SolverType.getSolverType(solverName);
        if (solverType == null) {
            ErrorReporter.errorAbort("Integration of solver \"" + solverName + "\" has not been implemented yet.");
        }

        this.useGraph = useGraph == null || useGraph.equals(Constants.TRUE);

        this.solveInParallel = !solverType.equals(SolverType.LOGIQL)
                && (solveInParallel == null || solveInParallel.equals(Constants.TRUE));

        this.collectStatistic = collectStatistic != null && !collectStatistic.equals(Constants.FALSE);

        // Sanitize the configuration if it needs.
        sanitizeConfiguration();
        System.out.println("Configuration: \nsolver: " + this.solverType.simpleName + "; \nuseGraph: "
                + this.useGraph + "; \nsolveInParallel: " + this.solveInParallel + ".");
    }

    protected void configureLattice(QualifierHierarchy qualHierarchy, Collection<Slot> slots) {
        LatticeBuilder latticeBuilder = new LatticeBuilder();
        lattice = latticeBuilder.buildLattice(qualHierarchy, slots);
    }

    /**
     * This method creates the default translator for a given backEndType.
     *
     * If customized serialization logic is needed, one can override this method and
     * return a customized translator corresponding to the given backEndType.
     *
     * @param solverType the type of solver that translator will associate with.
     * @param lattice the target type qualifier lattice.
     * @return A Translator compatible with the given solver type.
     */
    protected FormatTranslator<?, ?, ?> createFormatTranslator(SolverType solverType, Lattice lattice, ConstraintVerifier verifier) {
            return solverType.createDefaultFormatTranslator(lattice, verifier);
    }

    protected ConstraintGraph generateGraph(Collection<Slot> slots, Collection<Constraint> constraints,
            ProcessingEnvironment processingEnvironment) {
        GraphBuilder graphBuilder = new GraphBuilder(slots, constraints);
        ConstraintGraph constraintGraph = graphBuilder.buildGraph();
        return constraintGraph;
    }

    protected SolverAdapter<?> createSolverAdapter(SolverType solverType, Map<String, String> configuration,
            Collection<Slot> slots, Collection<Constraint> constraints, ProcessingEnvironment processingEnvironment,
            Lattice lattice, FormatTranslator<?, ?, ?> formatTranslator) {
            return solverType.createSolverAdapter(configuration, slots,
                    constraints, processingEnvironment, lattice, formatTranslator);
    }

    /**
     * This method is called when user doesn't separate constraints. Only one
     * back end will be created.
     * 
     * @return an InferenceSolution for the given slots/constraints
     */
    protected InferenceSolution solve() {
        solvingStart = System.currentTimeMillis();
        Map<Integer, AnnotationMirror> result = underlyingSolver.solve();
        solvingEnd = System.currentTimeMillis();
        StatisticRecorder.record(StatisticKey.OVERALL_NOGRAPH_SOLVING_TIME, (solvingEnd - solvingStart));
        StatisticRecorder.record(StatisticKey.ANNOTATOIN_SIZE, (long) result.size());
        PrintUtils.printResult(result);
        return new DefaultInferenceSolution(result);
    }

    /**
     * This method is called when user separates constraints, so that a list of
     * back end is created for all components.
     * 
     * @param constraintGraph
     * @param configuration
     * @param slots
     * @param constraints
     * @param qualHierarchy
     * @param processingEnvironment
     * @param formatTranslator
     * @return an InferenceSolution for the given slots/constraints
     */
    protected InferenceSolution graphSolve(ConstraintGraph constraintGraph,
            Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment, FormatTranslator<?, ?, ?> formatTranslator) {

        List<SolverAdapter<?>> underlyingSolvers = new ArrayList<SolverAdapter<?>>();
        StatisticRecorder.record(StatisticKey.GRAPH_SIZE, (long) constraintGraph.getIndependentPath().size());

        for (Set<Constraint> independentConstraints : constraintGraph.getIndependentPath()) {
            underlyingSolvers.add(createSolverAdapter(solverType, configuration, slots, independentConstraints,
                    processingEnvironment, lattice, formatTranslator));
        }
        // Clear constraint graph in order to save memory.
        this.constraintGraph = null;
        return mergeSolution(solve(underlyingSolvers));
    }

    /**
     * This method is called by graphSolve, and according to the boolean value
     * solveInParallel, corresponding solve method will be called.
     * 
     * @param underlyingSolvers
     * @return A list of Map that contains solutions from all back ends.
     */
    protected List<Map<Integer, AnnotationMirror>> solve(List<SolverAdapter<?>> underlyingSolvers) {

        List<Map<Integer, AnnotationMirror>> inferenceSolutionMaps = new LinkedList<Map<Integer, AnnotationMirror>>();

        if (underlyingSolvers.size() > 0) {
            if (solveInParallel) {
                try {
                    inferenceSolutionMaps = solveInparallel(underlyingSolvers);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            } else {
                inferenceSolutionMaps = solveInSequential(underlyingSolvers);
            }
        }
        return inferenceSolutionMaps;
    }

    /**
     * This method is called if user wants to call all underlying solvers in parallel.
     * 
     * @param underlyingSolvers
     * @return A list of Map that contains solutions from all underlying solvers.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    protected List<Map<Integer, AnnotationMirror>> solveInparallel(List<SolverAdapter<?>> underlyingSolvers)
            throws InterruptedException, ExecutionException {

        ExecutorService service = Executors.newFixedThreadPool(30);
        List<Future<Map<Integer, AnnotationMirror>>> futures = new ArrayList<Future<Map<Integer, AnnotationMirror>>>();

        solvingStart = System.currentTimeMillis();
        for (final SolverAdapter<?> underlyingSolver : underlyingSolvers) {
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
        solvingEnd = System.currentTimeMillis();
        StatisticRecorder.record(StatisticKey.OVERALL_PARALLEL_SOLVING_TIME, (solvingEnd - solvingStart));
        return solutions;
    }

    /**
     * This method is called if user wants to call all underlying solvers in sequence.
     * 
     * @param underlyingSolvers
     * @return A list of Map that contains solutions from all underlying solvers.
     */
    protected List<Map<Integer, AnnotationMirror>> solveInSequential(List<SolverAdapter<?>> underlyingSolvers) {

        List<Map<Integer, AnnotationMirror>> solutions = new ArrayList<>();

        solvingStart = System.currentTimeMillis();
        for (final SolverAdapter<?> underlyingSolver : underlyingSolvers) {
            solutions.add(underlyingSolver.solve());
        }
        solvingEnd = System.currentTimeMillis();
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

    /**
     * Sanitize and apply check of the configuration of solver based on a
     * specific type system. Sub-class solver of a specific type system may
     * override this method to sanitize the configuration of solver in the
     * context of that type system.
     */
    protected void sanitizeConfiguration() {

    }

    /**
     * Method that counts the size of each kind of constraint and slot.
     * 
     * @param slots
     * @param constraints
     * @return A map between name of constraint/slot and their counts.
     */
    private Map<String, Integer> recordSlotConstraintSize(final Collection<Slot> slots,
            final Collection<Constraint> constraints) {

        // Record constraint size
        StatisticRecorder.record(StatisticKey.CONSTRAINT_SIZE, (long) constraints.size());
        // Record slot size
        StatisticRecorder.record(StatisticKey.SLOTS_SIZE, (long) slots.size());
        Map<String, Integer> modelMap = new LinkedHashMap<>();

        for (Slot slot : slots) {
            if (slot instanceof ConstantSlot) {
                if (!modelMap.containsKey(SlotType.ConstantSlot.name())) {
                    modelMap.put(SlotType.ConstantSlot.name(), 1);
                } else {
                    modelMap.put(SlotType.ConstantSlot.name(), modelMap.get(SlotType.ConstantSlot.name()) + 1);
                }

            } else if (slot instanceof VariableSlot) {
                if (!modelMap.containsKey(SlotType.VariableSlot.name())) {
                    modelMap.put(SlotType.VariableSlot.name(), 1);
                } else {
                    modelMap.put(SlotType.VariableSlot.name(), modelMap.get(SlotType.VariableSlot.name()) + 1);
                }
            }
        }

        for (Constraint constraint : constraints) {
            String simpleName = constraint.getClass().getSimpleName();
            if (!modelMap.containsKey(simpleName)) {
                modelMap.put(simpleName, 1);
            } else {
                modelMap.put(simpleName, modelMap.get(simpleName) + 1);
            }
        }
        return modelMap;
    }

}
