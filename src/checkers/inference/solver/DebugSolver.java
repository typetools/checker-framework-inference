package checkers.inference.solver;

import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.serialization.ToStringSerializer;
import checkers.inference.util.InferenceUtil;

import org.checkerframework.framework.type.QualifierHierarchy;

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
    public InferenceSolution solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        List<String> output = new ArrayList<>();

        InferenceUtil.flushAllLoggers(true);
        final ToStringSerializer serializer = new ToStringSerializer(showAstPaths);
        final Map<Class<? extends Slot>, List<Slot>> typesToSlots = partitionSlots(slots);
        serializer.setIndent(1);

        StringBuilder stringBuilder = new StringBuilder();
        for (final Entry<Class<? extends Slot>, List<Slot>> entry : typesToSlots.entrySet()) {
            final Class<? extends Slot> type = entry.getKey();
            final List<Slot> slotsForType = entry.getValue();

            stringBuilder.append("\nCreated " + type.getSimpleName() + "\n");
            stringBuilder.append(serializer.serializeSlots(slotsForType, "\n"));
            System.out.print(stringBuilder.toString());
            output.add(stringBuilder.toString());
            stringBuilder.setLength(0);
        }

        stringBuilder.append("\n\nCreated these constraints:\n");
        stringBuilder.append(serializer.serializeConstraints(constraints, "\n"));
        stringBuilder.append("\n");

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
        typeToSlots.put(VariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(RefinementVariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(ExistentialVariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(CombVariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(ConstantSlot.class, new ArrayList<Slot>());

        for (final Slot slot : slots) {
            typeToSlots.get(slot.getClass()).add(slot);
        }

        return typeToSlots;
    }

}
