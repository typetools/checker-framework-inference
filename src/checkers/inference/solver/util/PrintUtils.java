package checkers.inference.solver.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
     * If set to true, will append inference solutions and statistics to output
     * files. This should be set to true for running experiment projects that
     * invoke javac and CF multiple times, so that the solutions and statistics
     * are not overwritten.
     *
     * The default value is false.
     */
    public static boolean appendWrite = false;

    /**
     * Creates and returns a string representation of inference solutions, where each row shows the id and the annotation of a slot.
     * @param solutions inference solutions: a map between slot IDs and annotation mirrors
     * @return string representation of inference solutions
     */
    private static String generateSolutionsString(Map<Integer, AnnotationMirror> solutions) {
        final AnnotatedTypeFactory atf = InferenceMain.getInstance().getRealTypeFactory();

        // string length of the highest slot ID, used to pad spaces for pretty formatting
        final int maxLength = String.valueOf(InferenceMain.getInstance().getSlotManager().getNumberOfSlots()).length();

        StringBuilder sb = new StringBuilder();

        for (Integer j : solutions.keySet()) {
            sb.append("SlotID: ");
            sb.append(j);
            for (int i = 0; i < maxLength + 2 - j.toString().length(); i++) {
                sb.append(" ");
            }
            sb.append("Annotation: ");
            sb.append(atf.getAnnotationFormatter().formatAnnotationMirror(solutions.get(j)));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Writes the given content to a file given by the writePath, with an option
     * to write in append mode as given by flag {@link #appendWrite}.
     *
     * @param writePath
     *            a full path to a file, including its file name
     * @param content
     *            content to be written
     */
    private static void writeToFile(String writePath, String content) {
        try {
            FileWriter fw = new FileWriter(writePath, appendWrite);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            pw.write(content);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Print the solved solutions out.
     */
    public static void printSolutions(Map<Integer, AnnotationMirror> solutions) {
        System.out.println();
        System.out.println("/***********************Solutions**************************/");
        System.out.flush();
        System.out.println(generateSolutionsString(solutions));
        System.out.flush();
        System.out.println("/**********************************************************/");
        System.out.println();
    }

    public static void writeSolutions(Map<Integer, AnnotationMirror> solutions) {
        String output = generateSolutionsString(solutions);

        String writePath = new File(new File("").getAbsolutePath()).toString() + "/result" + ".txt";
        writeToFile(writePath, output);

        System.out.println("Solutions have been written to: " + writePath);
    }

    /**
     * Print the statistics out.
     *
     * @param statistic
     * @param modelRecord
     */
    public static void printStatistic(Map<StatisticKey, Long> statistic,
            Map<String, Integer> modelRecord) {
        System.out.println();
        System.out.println("/***********************Statistic start*************************/");
        System.out.println(buildStatistic(statistic, modelRecord));
        System.out.flush();
        System.out.println("/**********************Statistic end****************************/");
        System.out.println();
    }

    public static void writeStatistic(Map<StatisticKey, Long> statistic,
            Map<String, Integer> modelRecord) {
        String writePath = new File(new File("").getAbsolutePath()).toString() + "/statistic.txt";
        writeToFile(writePath, buildStatistic(statistic, modelRecord));
        System.out.println("Statistics have been written to: " + writePath);
    }

    private static String buildStatistic(Map<StatisticKey, Long> statistic,
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

        statisticsText.append(basicInfo.toString());
        statisticsText.append(timingInfo.toString());
        return statisticsText.toString();
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

    private static String generateUnsolveableString(Collection<Constraint> unsatisfactoryConstraints) {
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

    public static void printUnsolvable(Collection<Constraint> unsatisfactoryConstraints) {
        if (unsatisfactoryConstraints == null || unsatisfactoryConstraints.isEmpty()) {
            System.out.println("The backend you used doesn't support explanation feature!");
            return;
        }

        System.out.println("=================================== Explanation Starts ===================================");
        System.out.println(generateUnsolveableString(unsatisfactoryConstraints));
        System.out.flush();
        System.out.println("=================================== Explanation Ends ================================");
    }

    public static void writeUnsolvable(Collection<Constraint> unsatisfactoryConstraints) {
        if (unsatisfactoryConstraints == null || unsatisfactoryConstraints.isEmpty()) {
            return;
        }

        String writePath = new File(new File("").getAbsolutePath()).toString() + "/unsolveable.txt";
        writeToFile(writePath, generateUnsolveableString(unsatisfactoryConstraints));
        System.out.println("Unsolveable constraints have been written to: " + writePath);
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