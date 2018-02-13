package sparta.checkers.propagation;

import checkers.inference.DefaultInferenceResult;
import checkers.inference.InferenceResult;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationBuilder;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.Slot.Kind;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import sparta.checkers.qual.Sink;
import sparta.checkers.qual.Source;

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
public abstract class IFlowSolver implements InferenceSolver {

    private static final Logger logger = Logger.getLogger(Logger.class.getName());

    private static final String PRINT_EMPTY_SINKS_KEY="print-empty-sinks";
    private static final String PRINT_EMPTY_SOURCES_KEY="print-empty-sources";

    private ProcessingEnvironment processingEnvironment;
    private Map<String, String> configuration;

    /**
     * Map of inferred Strings for an VariableSlot's id.
     */
    private final Map<Integer, Set<String>> inferredValues = new HashMap<>();

    // private final Map<String, Set<String>> flowPolicy = new HashMap<>();

    @Override
    public InferenceResult solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        this.processingEnvironment = processingEnvironment;
        this.configuration = configuration;

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
                        if (subtype.isVariable()) {
                            changed |= subtypePerms.addAll(supertypePerms);
                        }
                    } else {
                        if (supertype.isVariable()) {
                            changed |= supertypePerms.addAll(subtypePerms);
                        }
                    }
                } else if (constraint instanceof EqualityConstraint) {
                    Slot first = ((EqualityConstraint)constraint).getFirst();
                    Slot second = ((EqualityConstraint)constraint).getSecond();

                    Set<String> firstPerms = getInferredSlotPermissions(first);
                    Set<String> secondPerms = getInferredSlotPermissions(second);

                    if (first.isVariable()) {
                        changed |= firstPerms.addAll(secondPerms);
                    }

                    if (second.isVariable()) {
                        changed |= secondPerms.addAll(firstPerms);
                    }
                } else {
                    logger.info("Ignoring constraint type: " + constraint.getClass());
                }
            }
        }

        Map<Integer, AnnotationMirror> solutions = createAnnotations();

        return new DefaultInferenceResult(solutions);
    }

    private Map<Integer, AnnotationMirror> createAnnotations() {
        // Create annotations of the inferred sets.
        Map<Integer, AnnotationMirror> solutions = new HashMap<>();
        for (Entry<Integer, Set<String>> inferredEntry : inferredValues.entrySet()) {
            Set<String> strings = inferredEntry.getValue();
            if (!(strings.size() == 1 && strings.contains("ANY"))) {
                strings.remove("ANY");
                AnnotationMirror atm;
                if (isSinkSolver()) {
                    if (strings.size() == 0 && "false".equalsIgnoreCase(configuration.get(PRINT_EMPTY_SINKS_KEY))) {
                        continue;
                    }
                    atm = createAnnotationMirror(strings, Sink.class);
                } else {
                    if (strings.size() == 0 && "false".equalsIgnoreCase(configuration.get(PRINT_EMPTY_SOURCES_KEY))) {
                        continue;
                    }
                    atm = createAnnotationMirror(strings, Source.class);
                }
                solutions.put(inferredEntry.getKey(), atm);
            }
        }
        return solutions;
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
        if (slot.isVariable()) {
            if (slot.getKind() == Kind.EXISTENTIAL_VARIABLE) {
                throw new IllegalArgumentException("Unexpected variable type:" + slot);
            }
            return getFlowSet(((VariableSlot) slot).getId());

        } else if (slot.isConstant()) {
            Set<String> constantSet = new HashSet<>();
            for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    ((ConstantSlot) slot).getValue().getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals("value")) {
                    List<?> values = (List<?>) entry.getValue().getValue();
                    for (Object elem : values) {
                        String flowPermString = elem.toString();
                        flowPermString = flowPermString.substring(flowPermString.lastIndexOf(".") + 1);
                        flowPermString = flowPermString.replace("\"", "");
                        constantSet.add(String.valueOf(flowPermString).intern());
                    }
                }
            }
            // The set of constants should not be modified
            return Collections.unmodifiableSet(constantSet);
        } else {
            return new HashSet<>();
//            ErrorReporter.errorAbort("Found slot that was neither a variable or a constant: " + slot);
//            return null; // Dead code
        }
    }

    private AnnotationMirror createAnnotationMirror(Set<String> strings, Class<? extends Annotation> clazz) {
        AnnotationBuilder builder = new AnnotationBuilder( processingEnvironment, clazz);
        builder.setValue("value", strings.toArray());
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
