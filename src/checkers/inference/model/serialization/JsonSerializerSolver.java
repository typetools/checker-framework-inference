package checkers.inference.model.serialization;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceSolver;
import checkers.inference.TTIRun;
import checkers.inference.WeightInfo;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.types.QualifierHierarchy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * InferenceSolver that serializes constraints to a file in JSON format.
 * 
 * @author mcarthur
 *
 */

public class JsonSerializerSolver implements InferenceSolver {

    @Override
    public Map<Integer, AnnotationMirror> solve(List<Slot> slots,
            List<Constraint> constraints, 
            List<WeightInfo> weights,
            TTIRun ttiConfig, 
            QualifierHierarchy qualHierarchy) {

        AnnotationMirror top = qualHierarchy.getTopAnnotations().iterator().next();
        AnnotationMirror bottom = qualHierarchy.getTopAnnotations().iterator().next();
        SimpleAnnotationMirrorSerializer annotationSerializer = new SimpleAnnotationMirrorSerializer(top, bottom);
        JsonSerializer serializer = new JsonSerializer(slots, constraints, null, annotationSerializer);
        printJson(serializer, ttiConfig);

        return null;
    }

    protected void printJson(JsonSerializer serializer, TTIRun ttiConfig) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(serializer.generateConstraintFile());

        PrintWriter writer = null;
        try {
               // TODO: Parameterize this based on TTIConfig (command line input)
             writer = new PrintWriter(new FileOutputStream("./constraints.json"));
             writer.print(json);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
