package checkers.inference.solver;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.inference.InferenceSolution;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.serialization.ToStringSerializer;

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

    @Override
    public InferenceSolution solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        final ToStringSerializer serializer = new ToStringSerializer(showAstPaths);

        final Map<Class<? extends Slot>, List<Slot>> typesToSlots = partitionSlots(slots);

        for(final Entry<Class<? extends Slot>, List<Slot>> entry : typesToSlots.entrySet()) {
            final Class<? extends Slot> type = entry.getKey();
            final List<Slot> slotsForType = entry.getValue();

            System.out.println();
            System.out.println("Created " + type.getSimpleName());
            serializer.setIndent(1);
            System.out.println(serializer.serializeSlots(slotsForType, "\n"));
            serializer.setIndent(0);
        }

        System.out.println();
        System.out.println("Created these constraints:");

        serializer.setIndent(1);
        System.out.print(serializer.serializeConstraints(constraints, "\n"));
        serializer.setIndent(0);
        System.out.println();
        System.out.flush();

        return null;
    }

    //TODO: DO WE WANT TO ADD ConstantSlots to this?
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