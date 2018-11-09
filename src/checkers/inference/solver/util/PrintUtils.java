package checkers.inference.solver.util;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.AnnotatedTypeFactory;

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
import checkers.inference.model.ImplicationConstraint;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.LubVariableSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Serializer;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.serialization.ToStringSerializer;

/**
 * PrintUtils contains methods for printing and writing the solved results.
 */
public class PrintUtils {

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
            stream.print("SlotID: " + j);
            for (int i = 0; i < maxLength + 2 - j.toString().length(); i++) {
                stream.print(" ");
            }
            stream.println("Annotation: " + atf.getAnnotationFormatter()
                    .formatAnnotationMirror(solutions.get(j)));
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
        try (PrintStream out = FileUtils.getFilePrintStream(outFile, !noAppend)) {
            outputSolutions(out, solutions);
        }
        System.out.println("Solutions have been written to: " + outFile.getAbsolutePath() + "\n");
    }

    /**
     * Outputs the statistics to the given stream.
     * @param stream
     * @param statistics
     */
    private static void outputStatistics(PrintStream stream, Map<String, Long> statistics) {
        stream.println("====================== Statistics =======================");
        for (Map.Entry<String, Long> entry : statistics.entrySet()) {
            stream.println(entry.getKey() + ": " + entry.getValue());
        }
        stream.println("=========================================================");
    }

    /**
     * Print the statistics to screen.
     */
    public static void printStatistics(Map<String, Long> statistics) {
        outputStatistics(System.out, statistics);
    }

    /**
     * Write the statistics to a file called statistics.txt.
     *
     * @param statistics
     *            a map between stats keys and their long values.
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     */
    public static void writeStatistics(Map<String, Long> statistics, boolean noAppend) {
        File outFile = new File("statistics.txt");
        try (PrintStream out = FileUtils.getFilePrintStream(outFile, !noAppend)) {
            outputStatistics(out, statistics);
        }
        System.out.println("Statistics have been written to: " + outFile.getAbsolutePath() + "\n");
    }


    /**
     * Outputs unsat constraints to the given stream.
     * @param stream
     * @param unsatConstraints
     */
    private static void outputUnsatConstraints(PrintStream stream, Collection<Constraint> unsatConstraints) {
        stream.println("=================== Unsat Constraints ===================");

        ToStringSerializer toStringSerializer = new ToStringSerializer(false);
        toStringSerializer.setIndentationLevel(1);

        UniqueSlotCollector slotsCollector = new UniqueSlotCollector();

        // Print constraints and related slots
        stream.println("--- Constraints :");
        for (Constraint constraint : unsatConstraints) {
            stream.println(constraint.serialize(toStringSerializer));
            stream.println("\t" + constraint.getLocation());
        }

        // collect unique list of slots from all unsat constraints
        for (Constraint constraint : unsatConstraints) {
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
     * Print the unsat constraints and their related slots to screen.
     */
    public static void printUnsatConstraints(Collection<Constraint> unsatConstraints) {
        if (unsatConstraints == null || unsatConstraints.isEmpty()) {
            System.out.println("The backend you used doesn't support explanation feature!");
            return;
        }

        outputUnsatConstraints(System.out, unsatConstraints);
    }

    /**
     * Write the unsat constraints to a file called unsatConstraints.txt.
     *
     * @param unsatConstraints
     *            a collection of unsat constraints.
     * @param noAppend
     *            if set to true the file will be written over, and if set to
     *            false the file will be appended.
     */
    public static void writeUnsatConstraints(Collection<Constraint> unsatConstraints, boolean noAppend) {
        File outFile = new File("unsatConstraints.txt");
        try (PrintStream out = FileUtils.getFilePrintStream(outFile, !noAppend)) {
            outputUnsatConstraints(out, unsatConstraints);
        }
        System.out.println("Unsat constraints have been written to: " + outFile.getAbsolutePath() + "\n");
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
        public Void serialize(ImplicationConstraint constraint) {
            for (Constraint assumption : constraint.getAssumptions()) {
                assumption.serialize(this);
            }
            constraint.getConclusion().serialize(this);
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
