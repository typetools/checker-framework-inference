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

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.DefaultInferenceSolution;
import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.BackEnd;
import checkers.inference.solver.backend.BackEndType;
import checkers.inference.solver.constraintgraph.ConstraintGraph;
import checkers.inference.solver.constraintgraph.GraphBuilder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.LatticeBuilder;
import checkers.inference.solver.frontend.TwoQualifiersLattice;
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

public class GeneralSolver implements InferenceSolver {

    protected BackEndType backEndType;
    protected boolean useGraph;
    protected boolean solveInParallel;
    protected boolean collectStatistic;
    protected Lattice lattice;
    protected ConstraintGraph constraintGraph;
    protected BackEnd<?, ?> realBackEnd;

    // Timing variables:
    private long solvingStart;
    private long solvingEnd;

    @Override
    public InferenceSolution solve(Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        InferenceSolution solution = null;

        configureSolverArgs(configuration);
        configureLattice(qualHierarchy);
        Serializer<?, ?> serializer = createSerializer(backEndType, lattice);

        if (useGraph) {
            final long graphBuildingStart = System.currentTimeMillis();
            constraintGraph = generateGraph(slots, constraints, processingEnvironment);
            final long graphBuildingEnd = System.currentTimeMillis();
            StatisticRecorder.record(StatisticKey.GRAPH_GENERATION_TIME, (graphBuildingEnd - graphBuildingStart));
            solution = graphSolve(constraintGraph, configuration, slots, constraints, qualHierarchy,
                    processingEnvironment, serializer);
        } else {
            realBackEnd = createBackEnd(backEndType, configuration, slots, constraints, qualHierarchy,
                    processingEnvironment, lattice, serializer);
            solution = solve();
        }

        if (solution == null) {
            // Solution should never be null.
            ErrorReporter.errorAbort("Null solution detected!");
        }

        if (collectStatistic) {
            Map<String, Integer> modelRecord = recordSlotConstraintSize(slots, constraints);
            PrintUtils.printStatistic(StatisticRecorder.getStatistic(), modelRecord, backEndType,
                    useGraph, solveInParallel);
            PrintUtils.writeStatistic(StatisticRecorder.getStatistic(), modelRecord, backEndType,
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

        final String backEndName = configuration.get(SolverArg.backEndType.name());
        final String useGraph = configuration.get(SolverArg.useGraph.name());
        final String solveInParallel = configuration.get(SolverArg.solveInParallel.name());
        final String collectStatistic = configuration.get(SolverArg.collectStatistic.name());

        backEndType = backEndName == null ? BackEndType.MAXSAT : BackEndType.getBackEndType(backEndName);
        if (backEndType == null) {
            ErrorReporter.errorAbort("Back end \"" + backEndName + "\" has not been implemented yet.");
        }

        this.useGraph = useGraph == null || useGraph.equals(Constants.TRUE);

        this.solveInParallel = !backEndType.equals(BackEndType.LOGIQL)
                && (solveInParallel == null || solveInParallel.equals(Constants.TRUE));

        this.collectStatistic = collectStatistic != null && !collectStatistic.equals(Constants.FALSE);

        // Sanitize the configuration if it needs.
        sanitizeConfiguration();
        System.out.println("Configuration: \nback end type: " + this.backEndType.simpleName + "; \nuseGraph: "
                + this.useGraph + "; \nsolveInParallel: " + this.solveInParallel + ".");
    }

    protected void configureLattice(QualifierHierarchy qualHierarchy) {
        LatticeBuilder latticeBuilder = new LatticeBuilder();
        lattice = latticeBuilder.buildLattice(qualHierarchy);
    }

    protected TwoQualifiersLattice createTwoQualifierLattice(AnnotationMirror top, AnnotationMirror bottom) {
        LatticeBuilder latticeBuilder = new LatticeBuilder();
        return latticeBuilder.buildTwoTypeLattice(top, bottom);
    }

    /**
     * This method create the default serializer for a given backEndType.
     *
     * If customized serialization logic is needed, one can override this method and
     * return a customized serializer corresponding to the given backEndType.
     *
     * @param backEndType the backEndType that serializer will associate with.
     * @param lattice the target type qualifier lattice.
     * @return A Serializer compatible with the given backEndType.
     */
    protected Serializer<?, ?> createSerializer(BackEndType backEndType, Lattice lattice) {
            return backEndType.createDefaultSerializer(lattice);
    }

    protected ConstraintGraph generateGraph(Collection<Slot> slots, Collection<Constraint> constraints,
            ProcessingEnvironment processingEnvironment) {
        GraphBuilder graphBuilder = new GraphBuilder(slots, constraints);
        ConstraintGraph constraintGraph = graphBuilder.buildGraph();
        return constraintGraph;
    }

    protected BackEnd<?, ?> createBackEnd(BackEndType backEndType, Map<String, String> configuration,
            Collection<Slot> slots, Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy, ProcessingEnvironment processingEnvironment,
            Lattice lattice, Serializer<?, ?> serializer) {
            return backEndType.createBackEnd(configuration, slots,
                    constraints, qualHierarchy, processingEnvironment, lattice, serializer);
    }

    /**
     * This method is called when user doesn't separate constraints. Only one
     * back end will be created.
     * 
     * @return an InferenceSolution for the given slots/constraints
     */
    protected InferenceSolution solve() {
        solvingStart = System.currentTimeMillis();
        Map<Integer, AnnotationMirror> result = realBackEnd.solve();
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
     * @param serializer
     * @return an InferenceSolution for the given slots/constraints
     */
    protected InferenceSolution graphSolve(ConstraintGraph constraintGraph,
            Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment, Serializer<?, ?> serializer) {

        List<BackEnd<?, ?>> backEnds = new ArrayList<BackEnd<?, ?>>();
        StatisticRecorder.record(StatisticKey.GRAPH_SIZE, (long) constraintGraph.getIndependentPath().size());

        for (Set<Constraint> independentConstraints : constraintGraph.getIndependentPath()) {
            backEnds.add(createBackEnd(backEndType, configuration, slots, independentConstraints,
                    qualHierarchy, processingEnvironment, lattice, serializer));
        }
        // Clear constraint graph in order to save memory.
        this.constraintGraph = null;
        return mergeSolution(solve(backEnds));
    }

    /**
     * This method is called by graphSolve, and according to the boolean value
     * solveInParallel, corresponding solve method will be called.
     * 
     * @param backEnds
     * @return A list of Map that contains solutions from all back ends.
     */
    protected List<Map<Integer, AnnotationMirror>> solve(List<BackEnd<?, ?>> backEnds) {

        List<Map<Integer, AnnotationMirror>> inferenceSolutionMaps = new LinkedList<Map<Integer, AnnotationMirror>>();

        if (backEnds.size() > 0) {
            if (solveInParallel) {
                try {
                    inferenceSolutionMaps = solveInparallel(backEnds);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            } else {
                inferenceSolutionMaps = solveInSequential(backEnds);
            }
        }
        return inferenceSolutionMaps;
    }

    /**
     * This method is called if user wants to call all back ends in parallel.
     * 
     * @param backEnds
     * @return A list of Map that contains solutions from all back ends.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    protected List<Map<Integer, AnnotationMirror>> solveInparallel(List<BackEnd<?, ?>> backEnds)
            throws InterruptedException, ExecutionException {

        ExecutorService service = Executors.newFixedThreadPool(30);
        List<Future<Map<Integer, AnnotationMirror>>> futures = new ArrayList<Future<Map<Integer, AnnotationMirror>>>();

        solvingStart = System.currentTimeMillis();
        for (final BackEnd<?, ?> backEnd : backEnds) {
            Callable<Map<Integer, AnnotationMirror>> callable = new Callable<Map<Integer, AnnotationMirror>>() {
                @Override
                public Map<Integer, AnnotationMirror> call() throws Exception {
                    return backEnd.solve();
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
     * This method is called if user wants to call all back ends in sequential.
     * 
     * @param backEnds
     * @return A list of Map that contains solutions from all back ends.
     */
    protected List<Map<Integer, AnnotationMirror>> solveInSequential(List<BackEnd<?, ?>> backEnds) {

        List<Map<Integer, AnnotationMirror>> solutions = new ArrayList<>();

        solvingStart = System.currentTimeMillis();
        for (final BackEnd<?, ?> backEnd : backEnds) {
            solutions.add(backEnd.solve());
        }
        solvingEnd = System.currentTimeMillis();
        StatisticRecorder.record(StatisticKey.OVERALL_SEQUENTIAL_SOLVING_TIME, (solvingEnd - solvingStart));
        return solutions;
    }

    /**
     * This method merges all solutions from all back ends.
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
