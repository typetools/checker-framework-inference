package checkers.inference.solver;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.SolverFactory;
import checkers.inference.solver.backend.maxsat.MaxSatSolver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.LatticeBuilder;
import checkers.inference.solver.strategy.PlainSolvingStrategy;
import checkers.inference.solver.strategy.SolvingStrategy;
import checkers.inference.solver.util.NameUtils;
import checkers.inference.solver.util.PrintUtils;
import checkers.inference.solver.util.SolverArg;
import checkers.inference.solver.util.SolverEnvironment;
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
    protected boolean collectStatistic;
    protected String strategyName;
    protected String solverName;


    public enum SolverEngineArg implements SolverArg {
        solvingStrategy,
        solver,
        collectStatistic;
    }

    private final String BACKEND_PACKAGE_PATH = SolverFactory.class.getPackage().getName();
    private final String STRATEGY_PACKAGE_NAME = SolvingStrategy.class.getPackage().getName();

    protected SolverFactory createSolverFactory() {
        final String solverPackageName = BACKEND_PACKAGE_PATH + "." + solverName.toLowerCase();
        final String solverFactoryClassName = solverName + "SolverFactory";

        try {
            Class<?> SolverFactoryClass = Class.forName(solverPackageName + "." + solverFactoryClassName);
            return (SolverFactory) SolverFactoryClass.getConstructor().newInstance();
        } catch (Exception e) {
            ErrorReporter.errorAbort("Exceptions happends when creating the solver factory for " + solverName, e);
            // Dead code.
            return null;
        }
    }

    protected final SolvingStrategy createSolvingStrategy() {
        SolverFactory solverFactory = createSolverFactory();
        return createSolvingStrategy(solverFactory);
    }

    protected SolvingStrategy createSolvingStrategy(SolverFactory solverFactory) {
        final String strategyClassName = strategyName + "SolvingStrategy";

        try {
            Class<?> solverStrategyClass = Class.forName(STRATEGY_PACKAGE_NAME + "." + strategyClassName);
            return (SolvingStrategy) solverStrategyClass.getConstructor(SolverFactory.class).newInstance(solverFactory);
        } catch (Exception e) {
            ErrorReporter.errorAbort(e.getClass().getSimpleName() + " happends when creating [" + strategyName + "] solving strategy!", e);
            return null;
        }
    }

    @Override
    public final InferenceSolution solve(Map<String, String> configuration, Collection<Slot> slots,
            Collection<Constraint> constraints, QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        SolverEnvironment solverEnvironment = new SolverEnvironment(configuration, processingEnvironment);

        configureSolverEngineArgs(solverEnvironment);

        //TODO: Add solve timing statistic.
        Lattice lattice = new LatticeBuilder().buildLattice(qualHierarchy, slots);
        SolvingStrategy solvingStrategy = createSolvingStrategy();
        InferenceSolution solution = solvingStrategy.solve(solverEnvironment, slots, constraints, lattice);

        if (solution == null) {
            // Solution should never be null.
            ErrorReporter.errorAbort("Solution should never be null, but null solution detected!");
        }

        if (collectStatistic) {
            Map<String, Integer> modelRecord = recordSlotConstraintSize(slots, constraints);
            PrintUtils.printStatistic(StatisticRecorder.getStatistic(), modelRecord);
            PrintUtils.writeStatistic(StatisticRecorder.getStatistic(), modelRecord);
        }

        return solution;
    }

    /**
     * This method configures following arguments: solving strategy, and collectStatistic.
     * 
     * @param configuration
     */
    private void configureSolverEngineArgs(SolverEnvironment solverEnvironment) {
        String strategyName = solverEnvironment.getArg(SolverEngineArg.solvingStrategy);
        this.strategyName = strategyName == null ?
                NameUtils.getStrategyName(PlainSolvingStrategy.class)
                : strategyName;

        String solverName = solverEnvironment.getArg(SolverEngineArg.solver);
        this.solverName = solverName == null ?
                NameUtils.getSolverName(MaxSatSolver.class)
                : solverName;

        this.collectStatistic = solverEnvironment.getBoolArg(SolverEngineArg.collectStatistic);
        // Sanitize the configuration if it needs.
        sanitizeSolverEngineArgs();
    }

    /**
     * Sanitize and apply check of the configuration of solver based on a
     * specific type system. Sub-class solver of a specific type system may
     * override this method to sanitize the configuration of solver in the
     * context of that type system.
     */
    protected void sanitizeSolverEngineArgs() {
        //Intentionally empty.
    }

    //TODO: Move this method to the class responsible for statistic.
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

        final String CONSTANT_SLOT_NAME = ConstantSlot.class.getSimpleName();
        final String VARIABLE_SLOT_NAME = VariableSlot.class.getSimpleName();
        for (Slot slot : slots) {
            if (slot instanceof ConstantSlot) {
                if (!modelMap.containsKey(CONSTANT_SLOT_NAME)) {
                    modelMap.put(CONSTANT_SLOT_NAME, 1);
                } else {
                    modelMap.put(CONSTANT_SLOT_NAME, modelMap.get(CONSTANT_SLOT_NAME) + 1);
                }

            } else if (slot instanceof VariableSlot) {
                if (!modelMap.containsKey(VARIABLE_SLOT_NAME)) {
                    modelMap.put(VARIABLE_SLOT_NAME, 1);
                } else {
                    modelMap.put(VARIABLE_SLOT_NAME, modelMap.get(VARIABLE_SLOT_NAME) + 1);
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
