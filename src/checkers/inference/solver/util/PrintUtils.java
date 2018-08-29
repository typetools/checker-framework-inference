package checkers.inference.solver.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.javacutil.BugInCF;

import checkers.inference.InferenceMain;
import checkers.inference.model.ArithmeticConstraint;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.LubVariableSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Serializer;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.serialization.ToStringSerializer;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * PrintUtils contains methods for printing and writing the solved results.
 *
 * @author jianchu
 *
 */
public class PrintUtils {

    /**
     * Helper method which opens the given file and returns a PrintStream to the
     * file.
     * 
     * @param file
     *            a file to be written to.
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     * @return a PrintStream to the file.
     */
    private static PrintStream getFilePrintStream(File file, boolean noAppend) {
        try {
            return new PrintStream(new FileOutputStream(file, !noAppend));
        } catch (FileNotFoundException e) {
            throw new BugInCF("Cannot find file " + file);
        }
    }

    /**
     * Outputs the inference solutions to the given stream, where each row shows
     * the id and the annotation of a slot.
     * 
     * @param stream an output stream
     * @param solutions
     *            inference solutions: a map between slot IDs and annotation
     *            mirrors
     */
    private static void outputSolutions(PrintStream stream, Map<Integer, AnnotationMirror> solutions) {
        final AnnotatedTypeFactory atf = InferenceMain.getInstance().getRealTypeFactory();
        
        // string length of the highest slot ID, used to pad spaces for pretty formatting
        final int maxLength = String.valueOf(InferenceMain.getInstance().getSlotManager().getNumberOfSlots()).length();

        stream.println("======================= Solutions =======================");

        for (Integer j : solutions.keySet()) {
            stream.print("SlotID: ");
            stream.print(j);
            for (int i = 0; i < maxLength + 2 - j.toString().length(); i++) {
                stream.print(" ");
            }
            stream.print("Annotation: ");
            stream.println(atf.getAnnotationFormatter().formatAnnotationMirror(solutions.get(j)));
        }

        stream.println("=========================================================");
    }

    /**
     * Print the solved solutions to screen.
     */
    public static void printSolutions(Map<Integer, AnnotationMirror> solutions) {
        outputSolutions(System.out, solutions);
    }

    /**
     * Write the solved solutions to a file called solutions.txt.
     *
     * @param solutions
     *            a map between slot IDs to its solution annotation.
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     */
    public static void writeSolutions(Map<Integer, AnnotationMirror> solutions,
            boolean noAppend) {
        File outFile = new File("solutions.txt");
        PrintStream out = getFilePrintStream(outFile, noAppend);
        outputSolutions(out, solutions);
        out.close();
        System.out.println("Solutions have been written to: " + outFile.getAbsolutePath() + "\n");
    }

    private static void outputStatisticText(PrintStream stream,
            StatisticKey key, Map<StatisticKey, Long> statistic) {
        outputStatisticText(stream, key.toString(), statistic.get(key));
    }

    private static void outputStatisticText(PrintStream stream, String key,
            Long value) {
        stream.print(key.toLowerCase());
        stream.print(",");
        stream.println(value);
    }

    /**
     * Outputs the statistics to the given stream.
     * @param stream
     * @param statistics
     * @param modelRecord
     */
    private static void outputStatistics(PrintStream stream, Map<StatisticKey, Long> statistics,
            Map<String, Integer> modelRecord) {

        stream.println("====================== Statistics =======================");

        // Basic info
        outputStatisticText(stream, StatisticKey.SLOTS_SIZE, statistics);
        outputStatisticText(stream, StatisticKey.CONSTRAINT_SIZE, statistics);
        for (Map.Entry<String, Integer> entry : modelRecord.entrySet()) {
            outputStatisticText(stream, entry.getKey(), entry.getValue().longValue());
        }

        stream.println("=========================================================");
    }

    /**
     * Print the statistics to screen.
     */
    public static void printStatistics(Map<StatisticKey, Long> statistics,
            Map<String, Integer> modelRecord) {
        outputStatistics(System.out, statistics, modelRecord);
    }

    /**
     * Write the statistics to a file called statistics.txt.
     *
     * @param statistics
     *            a map between stats keys and their long values.
     * @param modelRecord
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     */
    public static void writeStatistics(Map<StatisticKey, Long> statistics,
            Map<String, Integer> modelRecord,
            boolean noAppend) {
        File outFile = new File("statistics.txt");
        PrintStream out = getFilePrintStream(outFile, noAppend);
        outputStatistics(out, statistics, modelRecord);
        out.close();
        System.out.println("Statistics have been written to: " + outFile.getAbsolutePath() + "\n");
    }


    private static String generateUnsatisfactoryConstraintsString(Collection<Constraint> unsatisfactoryConstraints) {
        ToStringSerializer toStringSerializer = new ToStringSerializer(false);
        SlotPrinter slotPrinter = new SlotPrinter(toStringSerializer);

        StringBuffer sb = new StringBuffer();

        // Print constraints and related slots
        sb.append("------------------ Unsatisfactory Constraints ------------------\n");
        for (Constraint constraint : unsatisfactoryConstraints) {
            sb.append("\t" + constraint.serialize(toStringSerializer)
                    + " \n\t\t" + constraint.getLocation().toString() + "\n");
        }
        sb.append("------------- Related Slots -------------\n");
        for (Constraint constraint : unsatisfactoryConstraints) {
            sb.append(constraint.serialize(slotPrinter));
        }

        return sb.toString();
    }

    /**
     * Outputs unsatisfactory constraints to the given stream.
     * @param stream
     * @param unsatisfactoryConstraints
     */
    private static void outputUnsatisfactoryConstraints(PrintStream stream, Collection<Constraint> unsatisfactoryConstraints) {
        stream.println("=========================================================");

        
        
        stream.println("/***********************Explanation************************/");
        stream.println(generateUnsatisfactoryConstraintsString(unsatisfactoryConstraints));
        stream.println("/**********************************************************/");
    }

    /**
     * Print the unsolveable constraints and their related slots to screen.
     */
    public static void printUnsatisfactoryConstraints(Collection<Constraint> unsatisfactoryConstraints) {
        if (unsatisfactoryConstraints == null || unsatisfactoryConstraints.isEmpty()) {
            System.out.println("The backend you used doesn't support explanation feature!");
            return;
        }

        outputUnsatisfactoryConstraints(System.out, unsatisfactoryConstraints);
    }

    /**
     * Write the statistics to a file called unsolveables.txt.
     *
     * @param unsatisfactoryConstraints
     *            a collection of unsatisfactory constraints.
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     */
    public static void writeUnsatisfactoryConstraints(Collection<Constraint> unsatisfactoryConstraints,
            boolean noAppend) {
        File outFile = new File("unsolveables.txt");
        PrintStream out = getFilePrintStream(outFile, noAppend);
        outputUnsatisfactoryConstraints(out, unsatisfactoryConstraints);
        out.close();
        System.out.println("Unsolveable constraints have been written to: " + outFile.getAbsolutePath() + "\n");
    }

    /**
     * Transitively prints all non-constant slots in a constraint. Each slot is only
     * printed once.
     */
    public static final class SlotPrinter implements Serializer<String, String> {

        /**Delegatee that serializes slots to string representation.*/
        private final ToStringSerializer toStringSerializer;
        /**Stores already-printed slots so they won't be printed again.*/
        private final Set<Slot> printedSlots;

        public SlotPrinter(final ToStringSerializer toStringSerializer) {
            this.toStringSerializer = toStringSerializer;
            printedSlots = new HashSet<>();
        }

        private String printSlotIfNotPrinted(Slot slot) {
            if (printedSlots.add(slot) && !(slot instanceof ConstantSlot)) {
                return "\t" + slot.serialize(toStringSerializer) +
                        "\n\t\t" + slot.getLocation() + "\n";
            } else {
                return "";
            }
        }

        @Override
        public String serialize(SubtypeConstraint constraint) {
            return constraint.getSubtype().serialize(this)
                    + constraint.getSupertype().serialize(this);
        }

        @Override
        public String serialize(EqualityConstraint constraint) {
            return constraint.getFirst().serialize(this)
                    + constraint.getSecond().serialize(this);
        }

        @Override
        public String serialize(ExistentialConstraint constraint) {
            return constraint.getPotentialVariable().serialize(this);
        }

        @Override
        public String serialize(InequalityConstraint constraint) {
            return constraint.getFirst().serialize(this)
                    + constraint.getSecond().serialize(this);
        }

        @Override
        public String serialize(ComparableConstraint comparableConstraint) {
            return comparableConstraint.getFirst().serialize(this)
                    + comparableConstraint.getSecond().serialize(this);
        }

        @Override
        public String serialize(CombineConstraint combineConstraint) {
            return combineConstraint.getResult().serialize(this)
                    + combineConstraint.getTarget().serialize(this)
                    + combineConstraint.getDeclared().serialize(this);
        }

        @Override
        public String serialize(PreferenceConstraint preferenceConstraint) {
            return preferenceConstraint.getVariable().serialize(this);
        }

        @Override
        public String serialize(ArithmeticConstraint arithmeticConstraint) {
            return arithmeticConstraint.getLeftOperand().serialize(this)
                    + arithmeticConstraint.getRightOperand().serialize(this)
                    + arithmeticConstraint.getResult().serialize(this);
        }

        @Override
        public String serialize(VariableSlot slot) {
            return printSlotIfNotPrinted(slot);
        }

        @Override
        public String serialize(ConstantSlot slot) {
            return "";
        }

        @Override
        public String serialize(ExistentialVariableSlot slot) {
            return slot.getPotentialSlot().serialize(this)
                    + slot.getAlternativeSlot().serialize(this)
                    + printSlotIfNotPrinted(slot);
        }

        @Override
        public String serialize(RefinementVariableSlot slot) {
            return slot.getRefined().serialize(this)
                    + printSlotIfNotPrinted(slot);
        }

        @Override
        public String serialize(CombVariableSlot slot) {
            return slot.getFirst().serialize(this)
                    + slot.getSecond().serialize(this)
                    + printSlotIfNotPrinted(slot);
        }

        @Override
        public String serialize(LubVariableSlot slot) {
            return slot.getLeft().serialize(this)
                    + slot.getRight().serialize(this)
                    + printSlotIfNotPrinted(slot);
        }
    }
}
