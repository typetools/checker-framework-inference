package sparta.checkers;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.javacutil.ErrorReporter;

import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 * Solver for solving Strings for @Source and @Sink annotations.
 *
 * This solver finds the Set of Strings that a VariableSlot should have.
 * There is both a sink solving mode and a source solving mode.
 *
 * For sink solving, subtype constraints cause all of the annotations from the LHS to be added to the RHS.
 * For example:
 * @Sink(INTERNET) String a = b;
 * The inferred sinks for b should now include INTERNET.
 *
 * For source solving, subtype constraints cause all of the annotations from the RHS to be added to the LHS.
 * @Source(INTERNET) String a;
 * b = a;
 * The inferred sources for b should now include INTERNET.
 *
 * For both modes, an equality constraint causes the Sets for both involved Slots
 * to be equal and include all Strings from either set.
 *
 * The algorithm processes list of constraints, adding the Strings to the inferredValues
 * map as needed. The entire list of constraints is processed repeatedly until the inferredValues map
 * no longer changes.
 *
 * @author mcarthur
 */
public abstract class SpartaSolver implements InferenceSolver {

    private static final Logger logger = Logger.getLogger(Logger.class.getName());

    private ProcessingEnvironment processingEnvironment;

    /**
     * Map of inferred Strings for an VariableSlot's id.
     */
    private Map<Integer, Set<String>> inferredValues = new HashMap<>();

    private Map<String, Set<String>> flowPolicy = new HashMap<>();

    @Override
    public Map<Integer, AnnotationMirror> solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        this.processingEnvironment = processingEnvironment;

        // Fixed point
        boolean changed = true;
        while (changed) {
            changed = false;

            for (Constraint constraint : constraints) {
                if (constraint instanceof SubtypeConstraint) {
                    Slot subtype = ((SubtypeConstraint)constraint).getSubtype();
                    Slot supertype = ((SubtypeConstraint)constraint).getSupertype();

                    Set<String> subtypePerms = getInferredSlotPermissions(subtype);
                    Set<String> supertypePerms = getInferredSlotPermissions(supertype);

                    if (isSinkSolver()) {
                        if (subtype instanceof VariableSlot) {
                            changed |= subtypePerms.addAll(supertypePerms);
                        }
                    } else {
                        if (supertype instanceof VariableSlot) {
                            changed |= supertypePerms.addAll(subtypePerms);
                        }
                    }
                } else if (constraint instanceof EqualityConstraint) {
                    Slot first = ((EqualityConstraint)constraint).getFirst();
                    Slot second = ((EqualityConstraint)constraint).getSecond();

                    Set<String> firstPerms = getInferredSlotPermissions(first);
                    Set<String> secondPerms = getInferredSlotPermissions(second);

                    if (first instanceof VariableSlot) {
                        changed |= firstPerms.addAll(secondPerms);
                    }

                    if (second instanceof VariableSlot) {
                        changed |= secondPerms.addAll(firstPerms);
                    }
                } else {
                    logger.info("Ignoring constraint type: " + constraint.getClass());
                }
            }
        }

        Map<Integer, AnnotationMirror> result = createAnnotations();

        return result;
    }


	private Map<Integer, AnnotationMirror> createAnnotations() {
		// Create annotations of the inferred sets.
		Map<Integer, AnnotationMirror> result = new HashMap<>();
		for (Entry<Integer, Set<String>> inferredEntry : inferredValues
				.entrySet()) {
			Set<String> Strings = inferredEntry.getValue();
			if (!(Strings.size() == 1 && Strings
					.contains("\"ANY\""))) {
				Strings.remove("\"ANY\"");
				AnnotationMirror atm;
				if (isSinkSolver()) {
					atm = createAnnotationMirror(Strings, Sink.class);
				} else {
					atm = createAnnotationMirror(Strings, Source.class);
				}
				result.put(inferredEntry.getKey(), atm);
			}
		}
		return result;
	}


    /**
     * Look up the set of inferred Strings for a Slot.
     *
     * If the Slot is a VariableSlot, return its entry in inferredValues.
     *
     * If the Slot is a ConstantSlot, return an unmodifiable set based 
     * on the Strings used in the constant slots value.
     *
     * @param slot The slot to lookup
     * @return The slots current Set of Strings.
     */
    private Set<String> getInferredSlotPermissions(Slot slot) {
        if (slot instanceof VariableSlot) {
            return getFlowSet(((VariableSlot) slot).getId());
        } else if (slot instanceof ConstantSlot) {
            Set<String> constantSet = new HashSet<>();
            for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                ((ConstantSlot) slot).getValue().getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals("value")) {
                    @SuppressWarnings("unchecked")
                    List<?> values = (List<?>) entry.getValue().getValue();
                    for (Object elem : values) {
                        String enumName = elem.toString();
                        enumName = enumName.substring(enumName.lastIndexOf(".") + 1);
                        constantSet.add(String.valueOf(enumName));
                    }
                }
            }

            return Collections.unmodifiableSet(constantSet);
        } else {
            return new HashSet<>();
//            ErrorReporter.errorAbort("Found slot that was neither a variable or a constant: " + slot);
//            return null; // Dead code
        }
    }

    private AnnotationMirror createAnnotationMirror(Set<String> Strings, Class<? extends Annotation> clazz) {
        AnnotationBuilder builder = new AnnotationBuilder( processingEnvironment, clazz);
        builder.setValue("value", Strings.toArray());
        return builder.build();
    }

    /**
     * Get the Set of Strings in aggregatedValues map for the given id.
     * Create the Set and add it to the map if it does not already exist.
     *
     * @param id The id of the VariableSlot
     * @return The set of Strings for the id
     */
    private Set<String> getFlowSet(int id) {
        if (inferredValues.containsKey(id)) {
            return inferredValues.get(id);
        } else {
            Set<String> newSet = new HashSet<>();
            inferredValues.put(id, newSet);
            return newSet;
        }
    }

    /**
     * Configure the mode of the solver.
     *
     * @return true if solving @Sink annotations, false if solving @Source.
     */
    public abstract boolean isSinkSolver();
}
