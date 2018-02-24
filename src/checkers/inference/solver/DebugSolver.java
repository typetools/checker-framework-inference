package checkers.inference.solver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.processing.ProcessingEnvironment;
import org.checkerframework.framework.type.QualifierHierarchy;
import checkers.inference.InferenceResult;
import checkers.inference.InferenceSolver;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.serialization.ToStringSerializer;
import checkers.inference.util.InferenceUtil;

/**
 * Debug solver prints out variables and constraints.
 *
 * @author mcarthur
 *
 */
public class DebugSolver implements InferenceSolver {

    private static final boolean showAstPaths = true; // System.getProperty("showAstPaths", "false").equalsIgnoreCase("true");
    public static final String constraintFile = "constraint-file";

    @Override
    public InferenceResult solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        List<String> output = new ArrayList<>();

        InferenceUtil.flushAllLoggers(true);
        final ToStringSerializer serializer = new ToStringSerializer(showAstPaths);
        final Map<Class<? extends Slot>, List<Slot>> typesToSlots = partitionSlots(slots);
        serializer.setIndentationLevel(1);

        StringBuilder stringBuilder = new StringBuilder();
        for (final Entry<Class<? extends Slot>, List<Slot>> entry : typesToSlots.entrySet()) {
            final Class<? extends Slot> type = entry.getKey();
            final List<Slot> slotsForType = entry.getValue();

            stringBuilder.append("\nCreated " + type.getSimpleName() + "\n");
            stringBuilder.append(serializer.serializeSlots(slotsForType, "\n\n"));
            stringBuilder.append("\n");

            System.out.print(stringBuilder.toString());
            output.add(stringBuilder.toString());
            stringBuilder.setLength(0);
        }

        stringBuilder.append("\nCreated these Constraints:\n");
        stringBuilder.append(serializer.serializeConstraints(constraints, "\n\n"));
        stringBuilder.append("\n\n");

        System.out.print(stringBuilder.toString());
        System.out.flush();

        output.add(stringBuilder.toString());
        stringBuilder = null;

        if (configuration.containsKey(constraintFile)) {
            String filename = configuration.get(constraintFile);
            try (FileWriter file = new FileWriter(new File(filename))) {
                for (String out : output)
                    file.write(out);
                file.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Map<Class<? extends Slot>, List<Slot>> partitionSlots(Collection<Slot> slots) {
        Map<Class<? extends Slot>, List<Slot>> typeToSlots = new LinkedHashMap<>();

        for (final Slot slot : slots) {
            Class<? extends Slot> slotClass = slot.getClass();
            // Create array list for the type of the slot when we first encounter a new slot type
            if (typeToSlots.get(slotClass) == null) {
                typeToSlots.put(slotClass, new ArrayList<Slot>());
            }

            typeToSlots.get(slotClass).add(slot);
        }

        return typeToSlots;
    }
}
