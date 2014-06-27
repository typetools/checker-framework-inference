package checkers.inference.solver;

import java.util.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.QualifierHierarchy;

import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import org.checkerframework.javacutil.AnnotationUtils;

/**
 * InferenceSolver FloodSolver implementation
 *
 * TODO: Parameters to configure where to push conflicts?
 *
 * @author mcarthur
 *
 */
public class PropagationSolver implements InferenceSolver {

    private QualifierHierarchy qualHierarchy;
    private Collection<Constraint> constraints;
    private Collection<Slot> slots;

    private AnnotationMirror defaultValue;
    private AnnotationMirror top;
    private AnnotationMirror bottom;

    @Override
    public Map<Integer, AnnotationMirror> solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        this.slots = slots;
        this.constraints = constraints;
        this.qualHierarchy = qualHierarchy;

        // TODO: This needs to be parameterized based on the type system

        this.top = qualHierarchy.getTopAnnotations().iterator().next();
        this.bottom = qualHierarchy.getBottomAnnotations().iterator().next();
        this.defaultValue = top;//qualHierarchy.getTopAnnotations().iterator().next();

        return solve();
    }

    private static class CVPair {
        Constraint constraint;
        VariableSlot variable;
        public CVPair(Constraint constraint, VariableSlot variableSlot) {
            this.constraint = constraint;
            this.variable = variableSlot;
        }
    }

    /**
     * Flood solve a list of constraints.
     *
     * 1) Find all variables that must be top (@TOP <: Var or VAR == @TOP)
     *
     * 2) Find all variables that must be bot (Var <: @BOT or VAR == @BOT)
     *
     * 3) From constraints, create propagation maps.
     *  These maps one variable to a list of other variables.
     *  If the key variable is a certain annotation the variables in the value list must also be that annotation.
     *  A map is create for subtype propagation and supertype propagation.
     *
     *  As an example, given a subtype propagation map of:
     *  @1 -> [ @2, @3 ]
     *
     *  If @1 was inferred to be @BOT, then @2 and @3 would also have to be bot.
     *
     * 4) Propagate the supertype values first
     *
     * 5) Propagate the subtype values second
     *
     * 6) Merge the results to get just one AnnotationMirror for each variable.
     *
     * @return Map of int variable id to its inferred AnnotationMirror value
     */
    public Map<Integer, AnnotationMirror> solve() {

        Set<VariableSlot> fixedBottom = new HashSet<VariableSlot>();
        Set<VariableSlot> fixedTop = new HashSet<VariableSlot>();
        Map<VariableSlot, List<CVPair>> superTypePropagation = new HashMap<VariableSlot, List<CVPair>>();
        Map<VariableSlot, List<CVPair>> subTypePropagation = new HashMap<VariableSlot, List<CVPair>>();

        preprocessConstraints(fixedBottom, fixedTop, superTypePropagation, subTypePropagation);
        for (VariableSlot var : fixedBottom) {
            if (var.getId() == 20056) {
                System.out.println("found");
            }
        }

        // Propagate supertype
        Set<VariableSlot> inferredTop = propagateValues(fixedTop, superTypePropagation);

        // Propagate subtype
        Set<VariableSlot> inferredBottom = propagateValues(fixedBottom, subTypePropagation);
        for (VariableSlot var : inferredBottom) {
            if (var.getId() == 20056) {
                System.out.println("found");
            }
        }

        return mergeResults(fixedBottom, fixedTop, inferredTop, inferredBottom);
    }

    /**
     * Perform steps 1-3 of flood solving.
     *
     * The parameters are the results of processing.
     *
     * fixedBottom and fixedTop contain relationships between variables and constants 
     * (the constant for bottom and the constant for top respectively)
     *
     * superTypePropagation and subTypePropagation
     *
     * @param fixedBottom Variables that must be bottom
     * @param fixedTop Variables that must be top
     * @param superTypePropagation Map, where if a key is a supertyp, all variables in the value must also be supertype
     * @param subTypePropagation Map, where if a key is a subtype, all variables in the value must also be subtypes
     */
    private void preprocessConstraints(Set<VariableSlot> fixedBottom,
            Set<VariableSlot> fixedTop,
            Map<VariableSlot, List<CVPair>> superTypePropagation,
            Map<VariableSlot, List<CVPair>> subTypePropagation) {

        for (Constraint constraint: constraints) {
            // Skip constraints that are just constants
            if (!checkContainsVariable(constraint)) {
                continue;
            }

            if (constraint instanceof EqualityConstraint) {
                EqualityConstraint equality = (EqualityConstraint) constraint;
                if (equality.getFirst() instanceof ConstantSlot) {
                    // Equal to a constant forces a constant
                    AnnotationMirror value = ((ConstantSlot) equality.getFirst()).getValue();
                    VariableSlot variable = (VariableSlot) equality.getSecond();
                    if (AnnotationUtils.areSame(value, top)) {
                        fixedTop.add(variable);
                    } else {
                        fixedBottom.add(variable);
                    }
                } else if (equality.getSecond() instanceof ConstantSlot) {
                    // Equal to a constant forces a constant
                    AnnotationMirror value = ((ConstantSlot) equality.getSecond()).getValue();
                    VariableSlot variable = (VariableSlot) equality.getFirst();
                    if (AnnotationUtils.areSame(value, top)) {
                        fixedTop.add(variable);
                    } else {
                        fixedBottom.add(variable);
                    }
                } else {
                    // Variable equality means values of one propagates to values of the other, for both subtype and supertype
                    addEntryToMap(superTypePropagation, (VariableSlot) equality.getFirst(), (VariableSlot) equality.getSecond(), constraint);
                    addEntryToMap(superTypePropagation, (VariableSlot) equality.getSecond(), (VariableSlot) equality.getFirst(), constraint);
                    addEntryToMap(subTypePropagation, (VariableSlot) equality.getFirst(), (VariableSlot) equality.getSecond(), constraint);
                    addEntryToMap(subTypePropagation, (VariableSlot) equality.getSecond(), (VariableSlot) equality.getFirst(), constraint);
                }
            } else if (constraint instanceof SubtypeConstraint) {
                SubtypeConstraint subtype = (SubtypeConstraint) constraint;
                if (subtype.getSubtype() instanceof ConstantSlot) {
                    // If top is a subtype of a variable, that variable is top
                    AnnotationMirror value = ((ConstantSlot) subtype.getSubtype()).getValue();
                    VariableSlot variable = (VariableSlot) subtype.getSupertype();
                    if (AnnotationUtils.areSame(value, top)) {
                        fixedTop.add(variable);
                    }
                } else if (subtype.getSupertype() instanceof ConstantSlot) {
                    // If a variable is a subtype of bottom, that variable is bottom
                    AnnotationMirror value = ((ConstantSlot) subtype.getSupertype()).getValue();
                    VariableSlot variable = (VariableSlot) subtype.getSubtype();
                    if (AnnotationUtils.areSame(value, bottom)) {
                        fixedBottom.add(variable);
                    }
                } else {
                    // If the RHS is top, the LHS must be top
                    addEntryToMap(superTypePropagation, (VariableSlot) subtype.getSubtype(), (VariableSlot) subtype.getSupertype(), constraint);
                    // If the LHS is bottom, the RHS must be bottom
                    addEntryToMap(subTypePropagation, (VariableSlot) subtype.getSupertype(), (VariableSlot) subtype.getSubtype(), constraint);
                }
            }
        }
    }

    /**
     * Given the inferred values, return a value for each slot.
     *
     * Variables will have conflicting values if the constraints were not solvable.
     *
     * This currently gives value precedence to fixedBottom, fixedTop, inferredBottom, inferredTop
     *
     * @return
     */
    private Map<Integer, AnnotationMirror> mergeResults(
            Set<VariableSlot> fixedBottom, Set<VariableSlot> fixedTop,
            Set<VariableSlot> inferredTop, Set<VariableSlot> inferredBottom) {

        Map<Integer, AnnotationMirror> results = new HashMap<Integer, AnnotationMirror>();
        for (Slot slot : slots) {
            if (slot instanceof VariableSlot) {
                VariableSlot vslot = (VariableSlot) slot;
                AnnotationMirror result;
                if (fixedBottom.contains(slot)) {
                    result = bottom;
                } else if (fixedTop.contains(slot)) {
                    result = top;
                } else if (inferredBottom.contains(slot)) {
                    result = bottom;
                } else if (inferredTop.contains(slot)) {
                    result = top;
                } else {
                    result = defaultValue;
                }
                if (result != defaultValue) {
                    results.put(vslot.getId(), result);
                }
            }
        }

        return results;
    }

    /**
     * Given starting fixed values, iterate on the propagation map
     * to propagate the resulting values.
     *
     * @param fixed The starting values that will trigger propagation
     * @param typePropagation Maps of value to list of other values that will be propagated when the key is triggered.
     *
     * @return All values that were fixed flooded/propagated to.
     */
    private Set<VariableSlot> propagateValues(Set<VariableSlot> fixed,
            Map<VariableSlot, List<CVPair>> typePropagation) {

        Set<VariableSlot> results = new HashSet<VariableSlot>();

        Set<VariableSlot> worklist = new HashSet<VariableSlot>(fixed);
        while (!worklist.isEmpty()) {
            VariableSlot variable = worklist.iterator().next();
            worklist.remove(variable);
            if (typePropagation.containsKey(variable)) {
                List<CVPair> inferred = typePropagation.get(variable);
                List<VariableSlot> inferredVars = new ArrayList<VariableSlot>();
                for (CVPair pair : inferred) {
                    inferredVars.add(pair.variable);
                }
                inferredVars.removeAll(results);
                results.addAll(inferredVars);
                worklist.addAll(inferredVars);
            }
        }
        return results;
    }

    private boolean checkContainsVariable(Constraint constraint) {
        boolean containsVariable = false;
        for (Slot slot : constraint.getSlots()) {
            if (slot instanceof VariableSlot) {
                containsVariable = true;
            }
        }
        return containsVariable;
    }

    void addEntryToMap(Map<VariableSlot,List<CVPair>> entries, VariableSlot key, VariableSlot value, Constraint constraint) {
        List<CVPair> valueList;
        if (entries.get(key) == null) {
            valueList = new ArrayList<CVPair>();
            entries.put(key, valueList);
        } else {
            valueList = entries.get(key);
        }
        valueList.add(new CVPair(constraint, value));
    }
}
