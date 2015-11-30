package checkers.inference.solver;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceOptions;
import checkers.inference.InferenceSolution;
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

import checkers.inference.InferenceSolver;


/**
 * Debug solver prints out variables and constraints.
 *
 * @author mcarthur
 *
 */

public class DebugSolver implements InferenceSolver {

    private static final boolean showAstPaths = true;//System.getProperty("showAstPaths", "false").equalsIgnoreCase("true");
    private static final int MAX_BUFFER_LENGTH = 10000;
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

        for (final Entry<Class<? extends Slot>, List<Slot>> entry : typesToSlots.entrySet()) {
            final Class<? extends Slot> type = entry.getKey();
            final List<Slot> slotsForType = entry.getValue();
            StringBuilder stringBuilder = new StringBuilder("\n");
            stringBuilder.append("Created " + type.getSimpleName() + "\n");
            stringBuilder.append(serializer.serializeSlots(slotsForType, "\n"));
            System.out.print(stringBuilder.toString());
            output.add(stringBuilder.toString());
        }

        StringBuilder stringBuilder = new StringBuilder("\n");
        stringBuilder.append("Created these constraints:\n");
        stringBuilder.append(serializer.serializeConstraints(constraints, "\n"));
        stringBuilder.append("\n");

        System.out.print(stringBuilder.toString());
        System.out.flush();

        output.add(stringBuilder.toString());

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

        for(final Slot slot : slots) {
            typeToSlots.get(slot.getClass()).add(slot);
        }

        return typeToSlots;
    }

}