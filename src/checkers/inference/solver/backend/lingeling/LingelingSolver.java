package checkers.inference.solver.backend.lingeling;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.lang.model.element.AnnotationMirror;

import org.sat4j.core.VecInt;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.backend.maxsat.MaxSatSolver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.ExternalSolverUtils;
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.Statistics;

/**
 * LingelingSolver is also a MaxSatSolver but it calls Lingeling SAT solver to solve the clauses. It
 * doesn't support soft constraint.
 *
 * @author jianchu
 *
 */
public class LingelingSolver extends MaxSatSolver {

    // Lingeling binary executable file should be located at JSR308/lingeling/lingeling.
    private final String lingeling = System.getenv().get("JSR308") + "/lingeling/lingeling";
    // record cnf integers in clauses. lingeling solver give the answer for all
    // the integers from 1 to the largest one. Some of them may be not in the
    // clauses.
    private Set<Integer> variableSet = new HashSet<Integer>();
    private static AtomicInteger nth = new AtomicInteger(0);
    private long serializationStart;
    private long serializationEnd;

    public LingelingSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, MaxSatFormatTranslator formatTranslator,
            Lattice lattice) {
        super(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }

    @Override
    public Map<Integer, AnnotationMirror> solve() {
        Map<Integer, AnnotationMirror> solutions = null;

        this.serializationStart = System.currentTimeMillis();
        encodeAllConstraints();
        encodeWellFormednessRestriction();
        this.serializationEnd = System.currentTimeMillis();

        buildCNFInput();
        collectVals();
        recordData();
        int localNth = nth.incrementAndGet();
        writeCNFInput("cnfdata" + localNth + ".txt");

        this.solvingStart = System.currentTimeMillis();
        int[] resultArray = getSolverOutput(localNth);
        // TODO What's the value of resultArray if there is no solution? Need to adapt this to
        // changes in the PR: https://github.com/opprop/checker-framework-inference/pull/128
        // , i.e. set solutions to null if there is no solution
        solutions = decode(resultArray);
        this.solvingEnd = System.currentTimeMillis();

        long solvingTime = solvingEnd - solvingStart;
        long serializationTime = serializationEnd - serializationStart;

        Statistics.addOrIncrementEntry("sat_serialization_time(ms)", serializationTime);
        Statistics.addOrIncrementEntry("sat_solving_time(ms)", solvingTime);

        return solutions;
    }

    /**
     * Create Lingeling process, and read output and error.
     *
     * @param localNth
     * @return and int array, which stores truth assignment for CNF predicate.
     */
    private int[] getSolverOutput(int localNth) {
        String[] command = { lingeling,
                CNFData.getAbsolutePath() + "/cnfdata" + localNth + ".txt" };

        final List<Integer> resultList = new ArrayList<Integer>();
        ExternalSolverUtils.runExternalSolver(command, stdOut -> parseStdOut(stdOut, resultList),
                stdErr -> ExternalSolverUtils.printStdStream(System.err, stdErr));

        // Java 8 style of List<Integer> to int[] conversion
        return resultList.stream().mapToInt(Integer::intValue).toArray();
    }

    private void parseStdOut(BufferedReader stdOut, List<Integer> resultList) {
        String line;

        try {
            while ((line = stdOut.readLine()) != null) {
                if (line.charAt(0) == 'v') {
                    for (String retval : line.split(" ")) {
                        if (!retval.equals("") && !retval.equals(" ") && !retval.equals("\n")
                                && !retval.equals("v")) {
                            int val = Integer.parseInt(retval);
                            if (variableSet.contains(Math.abs(val))) {
                                resultList.add(val);
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * For lingeling solve, it gives the solution from 1 to the largest variable. However, some
     * numbers in this range may not has corresponding slot id. This method stores the variables
     * that we really care about.
     */
    private void collectVals() {
        for (VecInt clause : this.hardClauses) {
            int[] clauseArray = clause.toArray();
            for (int i = 0; i < clauseArray.length; i++) {
                variableSet.add(Math.abs(clauseArray[i]));
            }
        }
    }

    @Override
    protected boolean shouldOutputCNF() {
        // We need the CNF output to pass to Lingeling
        // and so we unconditionally signal we want CNF output.
        return true;
    }

    private void recordData() {
        int totalClauses = hardClauses.size() + softClauses.size();
        int totalVariable = variableSet.size();
        Statistics.addOrIncrementEntry("cnf_clause_size", totalClauses);
        Statistics.addOrIncrementEntry("cnf_variable_size", totalVariable);
    }
}
