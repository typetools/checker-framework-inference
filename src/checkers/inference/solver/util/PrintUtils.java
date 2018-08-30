package checkers.inference.solver.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
        try (PrintStream out = getFilePrintStream(outFile, noAppend)) {
            outputSolutions(out, solutions);
        }
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
        try (PrintStream out = getFilePrintStream(outFile, noAppend)) {
            outputStatistics(out, statistics, modelRecord);
        }
        System.out.println("Statistics have been written to: " + outFile.getAbsolutePath() + "\n");
    }


    /**
     * Outputs unsatisfactory constraints to the given stream.
     * @param stream
     * @param unsatisfactoryConstraints
     */
    private static void outputUnsatisfactoryConstraints(PrintStream stream, Collection<Constraint> unsatisfactoryConstraints) {
        stream.println("============== Unsatisfactory Constraints ===============");

        ToStringSerializer toStringSerializer = new ToStringSerializer(false);
        toStringSerializer.setIndentationLevel(1);

        UniqueSlotCollector slotsCollector = new UniqueSlotCollector();

        // Print constraints and related slots
        stream.println("--- Constraints :");
        for (Constraint constraint : unsatisfactoryConstraints) {
            stream.println(constraint.serialize(toStringSerializer));
            stream.println("\t" + constraint.getLocation());
        }

        // collect unique list of slots from all unsat constraints
        for (Constraint constraint : unsatisfactoryConstraints) {
            constraint.serialize(slotsCollector);
        }

        stream.println("--- Related Slots :");
        for (VariableSlot slot : slotsCollector.getSlots()) {
            stream.println(toStringSerializer.getCurrentIndentString()
                    + slot.serialize(toStringSerializer) + " : "
                    + slot.getClass().getSimpleName());
            stream.println("\t" + slot.getLocation());
        }

        stream.println("=========================================================");
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
        try (PrintStream out = getFilePrintStream(outFile, noAppend)) {
            outputUnsatisfactoryConstraints(out, unsatisfactoryConstraints);
        }
        System.out.println("Unsolveable constraints have been written to: " + outFile.getAbsolutePath() + "\n");
    }

    /**
     * This class visits a collection of constraints and collects from the
     * constraints a list of unique non-constant slots present in the
     * constraints.
     */
    public static final class UniqueSlotCollector implements Serializer<Void, Void> {

        /** Stores a set of uniquely visited slots, sorted based on slot ID. */
        private final Set<VariableSlot> uniqueRelatedSlots;

        public UniqueSlotCollector() {
            uniqueRelatedSlots = new TreeSet<>();
        }

        public Set<VariableSlot> getSlots() {
            return uniqueRelatedSlots;
        }

        private void addSlotIfNotAdded(VariableSlot slot) {
            if (!(slot instanceof ConstantSlot)) {
                uniqueRelatedSlots.add(slot);
            }
        }

        @Override
        public Void serialize(SubtypeConstraint constraint) {
            constraint.getSubtype().serialize(this);
            constraint.getSupertype().serialize(this);
            return null;
        }

        @Override
        public Void serialize(EqualityConstraint constraint) {
            constraint.getFirst().serialize(this);
            constraint.getSecond().serialize(this);
            return null;
        }

        @Override
        public Void serialize(ExistentialConstraint constraint) {
            constraint.getPotentialVariable().serialize(this);
            return null;
        }

        @Override
        public Void serialize(InequalityConstraint constraint) {
            constraint.getFirst().serialize(this);
            constraint.getSecond().serialize(this);
            return null;
        }

        @Override
        public Void serialize(ComparableConstraint constraint) {
            constraint.getFirst().serialize(this);
            constraint.getSecond().serialize(this);
            return null;
        }

        @Override
        public Void serialize(CombineConstraint constraint) {
            constraint.getResult().serialize(this);
            constraint.getTarget().serialize(this);
            constraint.getDeclared().serialize(this);
            return null;
        }

        @Override
        public Void serialize(PreferenceConstraint constraint) {
            constraint.getVariable().serialize(this);
            return null;
        }

        @Override
        public Void serialize(ArithmeticConstraint constraint) {
            constraint.getLeftOperand().serialize(this);
            constraint.getRightOperand().serialize(this);
            constraint.getResult().serialize(this);
            return null;
        }

        @Override
        public Void serialize(ConstantSlot slot) {
            return null;
        }

        @Override
        public Void serialize(VariableSlot slot) {
            addSlotIfNotAdded(slot);
            return null;
        }

        @Override
        public Void serialize(ExistentialVariableSlot slot) {
            slot.getPotentialSlot().serialize(this);
            slot.getAlternativeSlot().serialize(this);
            addSlotIfNotAdded(slot);
            return null;
        }

        @Override
        public Void serialize(RefinementVariableSlot slot) {
            slot.getRefined().serialize(this);
            addSlotIfNotAdded(slot);
            return null;
        }

        @Override
        public Void serialize(CombVariableSlot slot) {
            slot.getFirst().serialize(this);
            slot.getSecond().serialize(this);
            addSlotIfNotAdded(slot);
            return null;
        }

        @Override
        public Void serialize(LubVariableSlot slot) {
            slot.getLeft().serialize(this);
            slot.getRight().serialize(this);
            addSlotIfNotAdded(slot);
            return null;
        }
    }
}
