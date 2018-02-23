package checkers.inference.solver.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

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
     * Print the solved solutions out.
     */
    public static void printSolutions(Map<Integer, AnnotationMirror> solutions) {

        final int maxLength = String.valueOf(InferenceMain.getInstance().getSlotManager().getNumberOfSlots()).length();
        StringBuilder printBuffer = new StringBuilder();

        System.out.println("/***********************Results****************************/");
        for (Integer j : solutions.keySet()) {
            printBuffer.append("SlotID: ");
            printBuffer.append(String.valueOf(j));
            for (int i = 0; i < maxLength + 2 - String.valueOf(j).length(); i++) {
                printBuffer.append(" ");
            }
            printBuffer.append("Annotation: ");
            printBuffer.append(solutions.get(j).toString());
            printBuffer.append("\n");
        }
        System.out.println(printBuffer.toString());
        System.out.flush();
        System.out.println("/**********************************************************/");
    }

    public static void writeResult(Map<Integer, AnnotationMirror> result) {

        final int maxLength = String.valueOf(InferenceMain.getInstance().getSlotManager().getNumberOfSlots()).length();
        StringBuilder printResult = new StringBuilder();

        for (Integer j : result.keySet()) {
            printResult.append("SlotID: ");
            printResult.append(String.valueOf(j));
            for (int i = 0; i < maxLength + 2 - String.valueOf(j).length(); i++) {
                printResult.append(" ");
            }
            printResult.append("Annotation: ");
            printResult.append(result.get(j).toString());
            printResult.append("\n");
        }

        File basePath = new File(new File("").getAbsolutePath());
        String writePath = basePath.getAbsolutePath() + "/result" + ".txt";
        File file = new File(writePath);
        PrintWriter pw;
        try {
            pw = new PrintWriter(file);
            pw.write(printResult.toString());
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Result has been written to: " + writePath);
    }

    private static StringBuilder buildStatistic(Map<StatisticKey, Long> statistic,
            Map<String, Integer> modelRecord) {

        StringBuilder statisticsText = new StringBuilder();
        StringBuilder basicInfo = new StringBuilder();
        StringBuilder timingInfo = new StringBuilder();

        // Basic info
        buildStatisticText(statistic, basicInfo, StatisticKey.SLOTS_SIZE);
        buildStatisticText(statistic, basicInfo, StatisticKey.CONSTRAINT_SIZE);
        for (Map.Entry<String, Integer> entry : modelRecord.entrySet()) {
            buildStatisticText(entry.getKey(), entry.getValue(), basicInfo);
        }

        statisticsText.append(basicInfo);
        statisticsText.append(timingInfo);
        return statisticsText;
    }

    /**
     * Print the statistics out.
     *
     * @param statistic
     * @param modelRecord
     */
    public static void printStatistic(Map<StatisticKey, Long> statistic,
            Map<String, Integer> modelRecord) {
        StringBuilder statisticsTest = buildStatistic(statistic, modelRecord);
        System.out.println("\n/***********************Statistic start*************************/");
        System.out.println(statisticsTest);
        System.out.flush();
        System.out.println("/**********************Statistic end****************************/");
    }

    public static void writeStatistic(Map<StatisticKey, Long> statistic,
            Map<String, Integer> modelRecord) {
        StringBuilder statisticsTest = buildStatistic(statistic, modelRecord);
        String writePath = new File(new File("").getAbsolutePath()).toString() + "/statistic.txt";
        try {
            PrintWriter pw = new PrintWriter(writePath);
            pw.write(statisticsTest.toString());
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Statistic has been written to: " + writePath);
    }

    private static void buildStatisticText(Map<StatisticKey, Long> statistic,
            StringBuilder statisticsText,
            StatisticKey key) {
        statisticsText.append(key.toString().toLowerCase());
        statisticsText.append(",");
        statisticsText.append(statistic.get(key));
        statisticsText.append("\n");
    }

    private static void buildStatisticText(String key, Integer value, StringBuilder statisticsText) {
        statisticsText.append(key.toLowerCase());
        statisticsText.append(",");
        statisticsText.append(value);
        statisticsText.append("\n");
    }

    public static void printUnsolvable(Collection<Constraint> unsatisfactoryConstraints) {
        if (unsatisfactoryConstraints == null || unsatisfactoryConstraints.isEmpty()) {
            System.out.println("The backend you used doesn't support explanation feature!");
            return;
        }

        ToStringSerializer toStringSerializer = new ToStringSerializer(false);
        SlotPrinter slotPrinter = new SlotPrinter(toStringSerializer);
        // Print constraints and related slots
        System.out.println("\n=================================== Explanation Starts=================================\n");
        System.out.println("------------------ Unsatisfactory Constraints ------------------\n");
        for (Constraint constraint : unsatisfactoryConstraints) {
            System.out.println("\t" + constraint.serialize(toStringSerializer) + " \n\t    " + constraint.getLocation().toString() + "\n");
        }
        System.out.println("------------- Related Slots -------------\n");
        for (Constraint c : unsatisfactoryConstraints) {
            c.serialize(slotPrinter);
        }
        System.out.println("=================================== Explanation Ends Here ================================");
    }

    /**
     * Transitively prints all non-constant slots in a constraint. Each slot is only
     * printed once.
     */
    public static final class SlotPrinter implements Serializer<Void, Void> {

        /**Delegatee that serializes slots to string representation.*/
        private final ToStringSerializer toStringSerializer;
        /**Stores already-printed slots so they won't be printed again.*/
        private final Set<Slot> printedSlots;


        public SlotPrinter(final ToStringSerializer toStringSerializer) {
            this.toStringSerializer = toStringSerializer;
            printedSlots = new HashSet<>();
        }

        private void printSlotIfNotPrinted(Slot slot) {
            if (printedSlots.add(slot) && !(slot instanceof ConstantSlot)) {
                System.out.println("\t" + slot.serialize(toStringSerializer) + " \n\t    " + slot.getLocation() + "\n");
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
        public Void serialize(ComparableConstraint comparableConstraint) {
            comparableConstraint.getFirst().serialize(this);
            comparableConstraint.getSecond().serialize(this);
            return null;
        }

        @Override
        public Void serialize(CombineConstraint combineConstraint) {
            combineConstraint.getResult().serialize(this);
            combineConstraint.getTarget().serialize(this);
            combineConstraint.getDeclared().serialize(this);
            return null;
        }

        @Override
        public Void serialize(PreferenceConstraint preferenceConstraint) {
            preferenceConstraint.getVariable().serialize(this);
            return null;
        }

        @Override
        public Void serialize(ArithmeticConstraint arithmeticConstraint) {
            arithmeticConstraint.getLeftOperand().serialize(this);
            arithmeticConstraint.getRightOperand().serialize(this);
            arithmeticConstraint.getResult().serialize(this);
            return null;
        }

        @Override
        public Void serialize(VariableSlot slot) {
            printSlotIfNotPrinted(slot);
            return null;
        }

        @Override
        public Void serialize(ConstantSlot slot) {
            return null;
        }

        @Override
        public Void serialize(ExistentialVariableSlot slot) {
            slot.getPotentialSlot().serialize(this);
            slot.getAlternativeSlot().serialize(this);
            printSlotIfNotPrinted(slot);
            return null;
        }

        @Override
        public Void serialize(RefinementVariableSlot slot) {
            slot.getRefined().serialize(this);
            printSlotIfNotPrinted(slot);
            return null;
        }

        @Override
        public Void serialize(CombVariableSlot slot) {
            slot.getFirst().serialize(this);
            slot.getSecond().serialize(this);
            printSlotIfNotPrinted(slot);
            return null;
        }
    }
}
