package checkers.inference.model.serialization;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.sat4j.core.VecInt;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;

/**
 * TODO: THIS IS NOT USEFUL UNTIL WE MAP EXISTENTIALVARIABLEIDS to POTENTIAL VAR
 */
public class CnfSerializerSolver implements InferenceSolver {

    private static final String FILE_KEY = "constraint-file";
    private static final String DEFAULT_FILE = "./constraints.json";
    private SlotManager slotManager;

    @Override
    public InferenceSolution solve(
           Map<String, String> configuration,
           Collection<Slot> slots,
           Collection<Constraint> constraints,
           QualifierHierarchy qualHierarchy,
           ProcessingEnvironment processingEnvironment) {

        AnnotationMirror top = qualHierarchy.getTopAnnotations().iterator().next();
        // AnnotationMirror bottom =
        // qualHierarchy.getBottomAnnotations().iterator().next();
        this.slotManager = InferenceMain.getInstance().getSlotManager();
        CnfVecIntSerializer cnfSerializer = new CnfVecIntSerializer(slotManager) {
            @Override
            protected boolean isTop(ConstantSlot constantSlot) {
                return AnnotationUtils.areSame(constantSlot.getValue(), top);
            }
        };

        String outFile = configuration.containsKey(FILE_KEY) ? configuration.get(FILE_KEY)
                                                              : DEFAULT_FILE;
        printCnf(new File(outFile), constraints, cnfSerializer);
        return null;
    }

    protected void printCnf(File outputFile, Collection<Constraint> constraints, CnfVecIntSerializer serializer) {
        try {
            int totalVars = slotManager.nextId();
            int totalConstraints = constraints.size();

            final BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            String header = makeComment(
                  "CNF File Generated by checkers.inference.serialization.CnfSerializerSolver\n"
                + "http://types.cs.washington.edu/checker-framework/\n"
                + "Generated: " + getDateString() + "\n"
                + "File Format: DIMACS CNF - http://www.satcompetition.org/2009/format-benchmarks2009.html"
            );
            writer.write(header);
            writer.newLine();

            writer.write(problem("cnf", totalVars, totalConstraints));
            writer.newLine();

            for (Constraint constraint : constraints) {
                final VecInt[] clauses = (VecInt[]) constraint.serialize(serializer);
                for (VecInt clause : clauses) {
                    writer.write(makeClause(clause));
                    writer.newLine();
                }
            }

            writer.flush();
            writer.close();

        } catch (IOException ioExc) {
            throw new RuntimeException("Error writing CNF File: " + outputFile.getAbsolutePath(), ioExc);
        }

    }

    private String getDateString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    private String makeComment(String commentBlock) {
        String [] lines = commentBlock.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        for(String line : lines) {
            sb.append("c " + line);
        }

        return sb.toString();
    }

    private String makeClause(VecInt clause) {
        StringBuilder sb = new StringBuilder();

        boolean first = true;

        for (int entry : clause.toArray()) {

            if (!first) {
                sb.append(' ');
            }

            sb.append(entry);
            first = false;
        }
        sb.append(' ');
        sb.append(0);

        return sb.toString();
    }

    private String problem(String format, int maxVariables, int maxConstraints) {
        return "p " + format + " " + maxVariables + " " + maxConstraints;
    }
}
