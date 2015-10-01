package checkers.inference.solver;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.LinkedHashMap;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.inference.InferenceSolution;
import checkers.inference.model.CombVariableSlot;
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

    @Override
    public InferenceSolution solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        InferenceUtil.flushAllLoggers(true);

        final ToStringSerializer serializer = new ToStringSerializer(showAstPaths);

        final Map<Class<? extends Slot>, List<Slot>> typesToSlots = partitionSlots(slots);

        StringBuilder stringBuilder = new StringBuilder();
        for(final Entry<Class<? extends Slot>, List<Slot>> entry : typesToSlots.entrySet()) {
            final Class<? extends Slot> type = entry.getKey();
            final List<Slot> slotsForType = entry.getValue();

            stringBuilder.append("\n");
            stringBuilder.append("Created " + type.getSimpleName());
            serializer.setIndent(1);
            stringBuilder.append(serializer.serializeSlots(slotsForType, "\n"));
            serializer.setIndent(0);

            if (stringBuilder.length() > MAX_BUFFER_LENGTH) {
                System.out.print(stringBuilder.toString());
                stringBuilder = new StringBuilder();
            }
        }

        stringBuilder.append("\n");
        stringBuilder.append("Created these constraints:");

        serializer.setIndent(1);
        stringBuilder.append(serializer.serializeConstraints(constraints, "\n"));
        serializer.setIndent(0);
        stringBuilder.append("\n");

        System.out.print(stringBuilder.toString());
        System.out.flush();

        return null;
    }


    public static Map<Class<? extends Slot>, List<Slot>> partitionSlots(Collection<Slot> slots) {
        Map<Class<? extends Slot>, List<Slot>> typeToSlots = new LinkedHashMap<>();
        typeToSlots.put(VariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(RefinementVariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(ExistentialVariableSlot.class, new ArrayList<Slot>());
        typeToSlots.put(CombVariableSlot.class, new ArrayList<Slot>());

        for(final Slot slot : slots) {
            typeToSlots.get(slot.getClass()).add(slot);
        }

        return typeToSlots;
    }

}