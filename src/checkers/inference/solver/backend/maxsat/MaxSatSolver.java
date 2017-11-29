package checkers.inference.solver.backend.maxsat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.sat4j.core.VecInt;
import org.sat4j.maxsat.WeightedMaxSatDecorator;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.model.Constraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverArg;
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.StatisticRecorder;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * MaxSatSolver calls MaxSatFormatTranslator that converts constraint into a list of
 * VecInt, then invoke Sat4j lib to solve the clauses, and decode the result.
 * 
 * @author jianchu
 *
 */
public class MaxSatSolver extends Solver<MaxSatFormatTranslator> {

    protected enum MaxSatSolverArg implements SolverArg {
        /**
         * Whether should print the CNF formulas.
         */
        outputCNF;
    }

    protected final SlotManager slotManager;
    protected final List<VecInt> hardClauses = new LinkedList<VecInt>();
    protected final List<VecInt> softClauses = new LinkedList<VecInt>();
    protected final File CNFData = new File(new File("").getAbsolutePath() + "/cnfData");
    protected StringBuilder CNFInput = new StringBuilder();

    private long serializationStart;
    private long serializationEnd;
    protected long solvingStart;
    protected long solvingEnd;

    public MaxSatSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, MaxSatFormatTranslator formatTranslator, Lattice lattice) {
        super(solverEnvironment, slots, constraints, formatTranslator,
                lattice);
        this.slotManager = InferenceMain.getInstance().getSlotManager();

        if (shouldOutputCNF()) {
            CNFData.mkdir();
        }
    }

    @Override
    public Map<Integer, AnnotationMirror> solve() {

        Map<Integer, AnnotationMirror> result = new HashMap<>();
        final WeightedMaxSatDecorator solver = new WeightedMaxSatDecorator(
                org.sat4j.pb.SolverFactory.newBoth());

        this.serializationStart = System.currentTimeMillis();
        // Serialization step:
        this.convertAll();
        this.serializationEnd = System.currentTimeMillis();

        for (Integer varSlotId : this.varSlotIds) {
            formatTranslator.generateOneHotClauses(hardClauses, varSlotId);
        }

        if (shouldOutputCNF()) {
            buildCNF();
            writeCNFInput();
        }
        // printClauses();
        configureSatSolver(solver);

        try {
            for (VecInt hardClause : hardClauses) {
                solver.addHardClause(hardClause);
            }

            this.hardClauses.clear();

            for (VecInt softclause : softClauses) {
                solver.addSoftClause(softclause);
            }

            this.solvingStart = System.currentTimeMillis();
            boolean isSatisfiable = solver.isSatisfiable();
            this.solvingEnd = System.currentTimeMillis();

            long solvingTime = solvingEnd - solvingStart;
            long serializationTime = serializationEnd - serializationStart;

            StatisticRecorder.recordSingleSerializationTime(serializationTime);
            StatisticRecorder.recordSingleSolvingTime(solvingTime);

            if (isSatisfiable) {
                result = decode(solver.model());
                // PrintUtils.printResult(result);
            } else {
                System.out.println("Not solvable!");
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * sat solver configuration Configure
     *
     * @param solver
     */
    private void configureSatSolver(WeightedMaxSatDecorator solver) {

        final int totalVars = (slotManager.getNumberOfSlots() * lattice.numTypes);
        final int totalClauses = hardClauses.size() + softClauses.size();

        solver.newVar(totalVars);
        solver.setExpectedNumberOfClauses(totalClauses);
        StatisticRecorder.record(StatisticKey.CNF_CLAUSE_SIZE, (long) totalClauses);
        countVariables();
        solver.setTimeoutMs(1000000);
    }

    /**
     * Convert constraints to list of VecInt.
     */
    @Override
    public void convertAll() {
        for (Constraint constraint : constraints) {
            collectVarSlots(constraint);
            for (VecInt res : constraint.serialize(formatTranslator)) {
                if (res != null && res.size() != 0) {
                    if (constraint instanceof PreferenceConstraint) {
                        softClauses.add(res);
                    } else {
                        hardClauses.add(res);
                    }
                }
            }
        }
    }

    protected Map<Integer, AnnotationMirror> decode(int[] solution) {
        Map<Integer, AnnotationMirror> result = new HashMap<>();
        for (Integer var : solution) {
            if (var > 0) {
                var = var - 1;
                int slotId = MathUtils.getSlotId(var, lattice);
                AnnotationMirror type = formatTranslator.decodeSolution(var, solverEnvironment.processingEnvironment);
                result.put(slotId, type);
            }
        }
        return result;
    }

    protected void countVariables() {

        Set<Integer> vars = new HashSet<Integer>();

        for (VecInt vi : hardClauses) {
            for (int i : vi.toArray()) {
                vars.add(i);
            }
        }
        StatisticRecorder.record(StatisticKey.CNF_VARIABLE_SIZE, (long) vars.size());
    }

    protected boolean shouldOutputCNF() {
        return solverEnvironment.getBoolArg(MaxSatSolverArg.outputCNF);
    }

    /**
     * Write CNF clauses into a string.
     */
    protected void buildCNF() {

        final int totalClauses = hardClauses.size();
        final int totalVars = slotManager.getNumberOfSlots() * lattice.numTypes;

        CNFInput.append("c This is the CNF input\n");
        CNFInput.append("p cnf ");
        CNFInput.append(totalVars);
        CNFInput.append(" ");
        CNFInput.append(totalClauses);
        CNFInput.append("\n");

        for (VecInt clause : hardClauses) {
            int[] literals = clause.toArray();
            for (int i = 0; i < literals.length; i++) {
                CNFInput.append(literals[i]);
                CNFInput.append(" ");
            }
            CNFInput.append("0\n");
        }
    }

    protected void writeCNFInput() {
        writeCNFInput("cnfdata.txt");
    }

    protected void writeCNFInput(String file) {
        String writePath = CNFData.getAbsolutePath() + "/" + file;
        File f = new File(writePath);
        PrintWriter pw;
        try {
            pw = new PrintWriter(f);
            pw.write(CNFInput.toString());
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * print all soft and hard clauses for testing.
     */
    protected void printClauses() {
        System.out.println("Hard clauses: ");
        for (VecInt hardClause : hardClauses) {
            System.out.println(hardClause);
        }
        System.out.println();
        System.out.println("Soft clauses: ");
        for (VecInt softClause : softClauses) {
            System.out.println(softClause);
        }
    }
}
