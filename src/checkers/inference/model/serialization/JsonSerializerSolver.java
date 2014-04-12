package checkers.inference.model.serialization;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.InferenceSolver;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * InferenceSolver that serializes constraints to a file in JSON format.
 *
 * @author mcarthur
 *
 */

public class JsonSerializerSolver implements InferenceSolver {

    private static final String FILE_KEY = "contraint-file";
    private static final String DEFAULT_FILE = "./constraints.json";
    private Map<String, String> configuration;

    @Override
    public Map<Integer, AnnotationMirror> solve(
            Map<String, String> configuration,
            List<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        this.configuration = configuration;
        AnnotationMirror top = qualHierarchy.getTopAnnotations().iterator().next();
        AnnotationMirror bottom = qualHierarchy.getBottomAnnotations().iterator().next();
        SimpleAnnotationMirrorSerializer annotationSerializer = new SimpleAnnotationMirrorSerializer(top, bottom);
        JsonSerializer serializer = new JsonSerializer(slots, constraints, null, annotationSerializer);
        printJson(serializer);

        return null;
    }

    protected void printJson(JsonSerializer serializer) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(serializer.generateConstraintFile());

        String outFile = configuration.containsKey(FILE_KEY) ?
                configuration.get(FILE_KEY)
                : DEFAULT_FILE;
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(outFile))) {
                writer.print(json);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
