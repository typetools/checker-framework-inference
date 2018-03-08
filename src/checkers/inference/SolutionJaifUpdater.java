package checkers.inference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.json.simple.parser.ParseException;

import checkers.inference.model.serialization.JsonDeserializer;
import org.plumelib.options.Option;
import org.plumelib.options.Options;

/**
 *
 * SolutionJaifUpdater takes in a solved json constraints file and a JAIF that contains
 * {@link @VarAnnot} annotations and creates a new JAIF by replacing the @VarAnnots
 * with the annotation for that @VarAnnot's id in the solution.
 *
 * @author mcarthur
 */
public class SolutionJaifUpdater {

    public static final String CHECKERS_INFERENCE_QUALS_VAR_ANNOT = "@checkers.inference.qual.VarAnnot(";
    @Option("[filename] the input jaif.")
    public static String jaifFilename = "default.jaif";

    @Option("[filename] the original JSON file")
    public static String originalJson;

    @Option("[filename] the input solved constraints json filename.")
    public static String solvedJson;

    @Option("[filename] the output filename for the solved jaif.")
    public static String outputFilename = "output.jaif";

    @Option("The string representation of the top annotation in the hierarchy (e.g. @Nullable)")
    public static String topAnnotation;

    @Option("The string representation of the bottom annotation in the hierarchy (e.g. @NonNull)")
    public static String botAnnotation;

    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options("SolutionJaifUpdator [options]", SolutionJaifUpdater.class);
        options.parse(true, args);
        if (solvedJson == null || originalJson == null ||  outputFilename == null || topAnnotation == null || botAnnotation == null) {
            System.out.println("A required argument was not found.");
            options.printUsage();
            System.exit(1);
        }

        String solvedJsonStr = readFile(solvedJson);
        JsonDeserializer solvedDeserializer = new JsonDeserializer(null, solvedJsonStr);

        Map<String, Boolean> existentialValues = getExistentialValues(originalJson, solvedDeserializer);
        Map<String, String> solvedValues = getSolvedValues(solvedDeserializer, topAnnotation, botAnnotation);
        updateJaif(solvedValues, existentialValues, jaifFilename, outputFilename);
    }

    /**
     * Parses the inference.jaif file provided by verigames.jar and updates the variable values
     * with a boolean of true/false depending on the results obtained from the updates xml file
     * after the user plays the game.
     * @param values Map<String, Boolean> where the integer is the variable id and the boolean
     * is the value to replace the variable id with.
     * @param existentialValues
     * @throws FileNotFoundException thrown if the file inference.jaif is not found in the current
     * directory.
     */
    private static void updateJaif(Map<String, String> values, Map<String, Boolean> existentialValues,
                                   String jaifPath, String outputFile) throws FileNotFoundException {
        if (values == null) {
            throw new IllegalArgumentException("Map passed must not be null");
        }

        try (Scanner in = new Scanner(new File(jaifPath));
             PrintStream out = new PrintStream(new File(outputFile))) {

            while (in.hasNextLine()) {
                String line = in.nextLine();
                int start = -1;
                if ((start = line.indexOf(CHECKERS_INFERENCE_QUALS_VAR_ANNOT)) != -1) {

                    int end = start + (CHECKERS_INFERENCE_QUALS_VAR_ANNOT.length());
                    String key = line.substring(end, line.length() - 1);

                    if (values.get(key) == null) {
                        System.out.println("Warning: Could not find value for " + key + " using supertype, skipping");
                    } else {
                        Boolean exists = existentialValues.get(key);
                        if (exists == null || exists) {
                            out.print(line.substring(0, start));
                            out.println(values.get(key));
                        }
                    }

                } else
                    out.println(line);
            }
        }
    }

    private static final Map<String, Boolean> getExistentialValues(String originalJsonFilename, JsonDeserializer solvedDeserializer) throws IOException, ParseException {
        String json = readFile(originalJsonFilename);
        JsonDeserializer deserializer = new JsonDeserializer(null, json);
        List<String> allPotentialVariables = deserializer.getPotentialVariables();
        Set<String> enabledVars = solvedDeserializer.getEnabledVars();

        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String potentialVar : allPotentialVariables) {
            out.put(potentialVar, enabledVars.contains(potentialVar));
        }

        return out;
    }

    private static final Map<String, String> getSolvedValues(JsonDeserializer deserializer, String top, String bottom) throws IOException, ParseException {
        Map<String, String> values = deserializer.getAnnotationValues();
        Map<String, String> results = new HashMap<>();
        for (Map.Entry<String, String> entry: values.entrySet()) {
            String value = entry.getValue().equals("0") ? bottom : top;
            results.put(entry.getKey(), value);
        }

        return results;
    }

    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, Charset.defaultCharset());
    }
}
