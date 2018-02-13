package checkers.inference.solver.backend.lingeling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.StatisticRecorder;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * LingelingSolver is also a MaxSatSolver but it calls Lingeling SAT solver to
 * solve the clauses. It doesn't support soft constraint.
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
            Collection<Constraint> constraints, MaxSatFormatTranslator formatTranslator, Lattice lattice) {
        super(solverEnvironment, slots, constraints, formatTranslator,
                lattice);
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
        try {
            int[] resultArray = getOutPut_Error(lingeling + " " + CNFData.getAbsolutePath() + "/cnfdata"
                    + localNth + ".txt");
            // TODO What's the value of resultArray if there is no solution? Need to adapt this to
            // changes in the PR: https://github.com/opprop/checker-framework-inference/pull/128
            // , i.e. set solutions to null if there is no solution
            solutions = decode(resultArray);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        this.solvingEnd = System.currentTimeMillis();

        long solvingTime = solvingEnd - solvingStart;
        long serializationTime = serializationEnd - serializationStart;

        StatisticRecorder.recordSingleSerializationTime(serializationTime);
        StatisticRecorder.recordSingleSolvingTime(solvingTime);

        return solutions;
    }

    /**
     * Create Lingeling process, and read output and error.
     *
     * @param command
     * @return and int array, which stores truth assignment for CNF predicate.
     * @throws IOException
     * @throws InterruptedException
     */
    private int[] getOutPut_Error(String command) throws IOException, InterruptedException {

        final List<Integer> resultList = new ArrayList<Integer>();
        final Process p = Runtime.getRuntime().exec(command);

        Thread getOutPut = new Thread() {
            @Override
            public void run() {
                String s = "";
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    while ((s = stdInput.readLine()) != null) {
                        if (s.charAt(0) == 'v') {
                            for (String retval : s.split(" ")) {
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
        };
        getOutPut.start();
        Thread getError = new Thread() {
            @Override
            public void run() {
                String s = "";
                StringBuilder sb = new StringBuilder();
                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                try {
                    while ((s = stdError.readLine()) != null) {
                        sb.append(s);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        getError.start();
        getOutPut.join();
        getError.join();
        p.waitFor();
        // Cannot convert from Integer[] to int[] directly...
        int[] resultArray = new int[resultList.size()];
        for (int i = 0; i < resultList.size(); i++) {
            resultArray[i] = resultList.get(i);
        }
        return resultArray;
    }

    /**
     * For lingeling solve, it gives the solution from 1 to the largest
     * variable. However, some numbers in this range may not has corresponding
     * slot id. This method stores the variables that we really care about.
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
        StatisticRecorder.record(StatisticKey.CNF_CLAUSE_SIZE, (long) totalClauses);
        StatisticRecorder.record(StatisticKey.CNF_VARIABLE_SIZE, (long) totalVariable);
    }
}
