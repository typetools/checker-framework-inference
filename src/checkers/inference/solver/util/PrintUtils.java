package checkers.inference.solver.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceMain;
import checkers.inference.solver.util.StatisticRecorder.StatisticKey;

/**
 * PrintUtils contains methods for printing and writing the solved results.
 * 
 * @author jianchu
 *
 */
public class PrintUtils {

    /**
     * Print the solved result out.
     *
     * @param result
     */
    public static void printResult(Map<Integer, AnnotationMirror> result) {

        final int maxLength = String.valueOf(InferenceMain.getInstance().getSlotManager().getNumberOfSlots()).length();
        StringBuilder printResult = new StringBuilder();

        System.out.println("/***********************Results****************************/");
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
        System.out.println(printResult.toString());
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
     * @param solverType
     * @param useGraph
     * @param solveInParallel
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
}
