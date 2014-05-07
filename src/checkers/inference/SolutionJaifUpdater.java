package checkers.inference;

import checkers.inference.model.serialization.JsonDeserializer;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import plume.Option;
import plume.Options;

/**
 *
 * SolutionJaifUpdater takes in a solved json constraints file and a JAIF that contains
 * {@link @VarAnnot} annotations and creates a new JAIF by replacing the @VarAnnots
 * with the annotation for that @VarAnnot's id in the solution.
 *
 * @author mcarthur
 */
public class SolutionJaifUpdater {

    public static final String CHECKERS_INFERENCE_QUALS_VAR_ANNOT = "@checkers.inference.quals.VarAnnot(";
    @Option("[filename] the input jaif.")
    public static String jaifFilename = "default.jaif";

    @Option("[filename] the input solved constraints json filename.")
    public static String jsonFilename;

    @Option("[filename] the output filename for the solved jaif.")
    public static String outputFilename = "output.jaif";

    @Option("The string representation of the top annotation in the hierarchy (e.g. @Nullable)")
    public static String topAnnotation;

    @Option("The string representation of the bottom annotation in the hierarchy (e.g. @NonNull)")
    public static String botAnnotation;

    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options("SolutionJaifUpdator [options]", SolutionJaifUpdater.class);
        options.parse_or_usage(args);
        if (jsonFilename == null || outputFilename == null || topAnnotation == null || botAnnotation == null) {
            options.print_usage("A required argument was not found.");
            System.exit(1);
        }

        Map<String, String> solvedValues = getSolvedValues(jsonFilename, topAnnotation, botAnnotation);
        updateJaif(solvedValues, jaifFilename, outputFilename);
    }

    /**
     * Parses the inference.jaif file provided by verigames.jar and updates the variable values
     * with a boolean of true/false depending on the results obtained from the updates xml file
     * after the user plays the game.
     * @param values Map<String, Boolean> where the integer is the variable id and the boolean
     * is the value to replace the variable id with.
     * @throws FileNotFoundException thrown if the file inference.jaif is not found in the current
     * directory.
     */
    private static void updateJaif(Map<String, String> values, String jaifPath, String outputFile) throws FileNotFoundException {
        if(values == null) {
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
                        out.print(line.substring(0, start));
                        out.println(values.get(key));
                    }

                } else
                    out.println(line);
            }
        }
    }

    private static final Map<String, String> getSolvedValues(String jsonFilename, String top, String bottom) throws IOException, ParseException {

        String json = readFile(jsonFilename);
        JsonDeserializer deserializer = new JsonDeserializer(null, json);
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
