package checkers.inference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;


/**
 * Debug solver prints out variables and constraints.
 *
 * @author mcarthur
 *
 */

public class DebugSolver implements InferenceSolver {

    @Override
    public Map<Integer, AnnotationMirror> solve(
            Map<String, String> configuration,
            List<Slot> slots,
            List<Constraint> constraints,
            QualifierHierarchy qualHierarchy) {

        System.out.println("Created variables:");
        for (Slot slot: slots) {
            if (slot.getClass().equals(VariableSlot.class)) {
                VariableSlot varSlot = (VariableSlot) slot;
                System.out.print(varSlot.getId());
                System.out.println(": merged to -> " + varSlot.getMergedToSlots());
            }
        }

        System.out.println();
        System.out.println("Created refinement variables:");
        for (Slot slot: slots) {
            if (slot.getClass().equals(RefinementVariableSlot.class)) {
                RefinementVariableSlot refSlot = (RefinementVariableSlot) slot;
                System.out.println(refSlot.getId() + ": refines " + Arrays.asList(refSlot.getRefined()) +
                        ": merged to -> " + refSlot.getMergedToSlots());
            }
        }

        System.out.println();
        System.out.println("Created comb & merge variables:");
        for (Slot slot: slots) {
            if (slot.getClass().equals(CombVariableSlot.class)) {
                CombVariableSlot refSlot = (CombVariableSlot) slot;
                System.out.println(refSlot.getId() + ": merges " + Arrays.asList(refSlot.getFirst(), refSlot.getSecond()) +
                        ": merged to -> " + refSlot.getMergedToSlots());
            }
        }

        System.out.println();
        System.out.println("Created these constarints:");
        for (Constraint constraint: constraints) {
            System.out.println(constraint);
        }

        return null;
    }

}
