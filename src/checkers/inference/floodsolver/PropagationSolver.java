package checkers.inference.floodsolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceSolver;
import checkers.inference.TTIRun;
import checkers.inference.WeightInfo;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.types.QualifierHierarchy;

/**
 * InferenceSolver FloodSolver implementation
 *  
 * TODO: TTI Run parameters to configure where to push conflicts?
 * 
 * @author mcarthur
 *
 */
public class PropagationSolver implements InferenceSolver {
    
    private QualifierHierarchy qualHierarchy;
    private TTIRun ttiConfig;
    private List<WeightInfo> weights;
    private List<Constraint> constraints;
    private List<Slot> slots;
    
    private AnnotationMirror defaultValue;
    private AnnotationMirror top;
    private AnnotationMirror bottom;

    @Override
    public Map<Integer, AnnotationMirror> solve(List<Slot> slots,
            List<Constraint> constraints, List<WeightInfo> weights,
            TTIRun ttiConfig, QualifierHierarchy qualHierarchy) {
        
        this.slots = slots;
        this.constraints = constraints;
        this.weights = weights;
        this.ttiConfig = ttiConfig;
        this.qualHierarchy = qualHierarchy;
        
        // TODO: This needs to be parameterized based on the type system 
        this.defaultValue = qualHierarchy.getTopAnnotations().iterator().next();
        this.top = qualHierarchy.getTopAnnotations().iterator().next();
        this.bottom = qualHierarchy.getBottomAnnotations().iterator().next();
        
        return solve();
    }
    
    /**
     * Flood solve a list of constraints.
     * 
     * 1) Find all variables that must be top (@TOP <: Var or VAR == @TOP)
     * 2) Find all variables that must be top (@BOT <: Var or VAR == @BOT)
     * 3) From constraints, create propagation maps.  These maps one variable to a list of other variables that will
     * have the must have the same value if the initial value is met.
     * Create one of these maps for subtype propagation and one for supertype propagation
     * 4) Propagate the supertype values first
     * 5) Propagate the subtype values second
     * 6) Merge the results to get just one AnnotationMirror for each variable.
     * 
     * Find all fixedTop variables 
     * 
     * @return
     */
    public Map<Integer, AnnotationMirror> solve() {
        
        Set<VariableSlot> fixedBottom = new HashSet<VariableSlot>();
        Set<VariableSlot> fixedTop = new HashSet<VariableSlot>();
        Map<VariableSlot, List<VariableSlot>> superTypePropagation = new HashMap<VariableSlot, List<VariableSlot>>();
        Map<VariableSlot, List<VariableSlot>> subTypePropagation = new HashMap<VariableSlot, List<VariableSlot>>();

        preprocessConstraints(fixedBottom, fixedTop, superTypePropagation,
                subTypePropagation);

        Set<VariableSlot> inferredTop = new HashSet<VariableSlot>();
        Set<VariableSlot> inferredBottom = new HashSet<VariableSlot>();

        // Propagate supertype
        propagateValues(fixedTop, superTypePropagation, inferredTop);

        // Propagate subtype
        propagateValues(fixedBottom, subTypePropagation, inferredBottom);

        return mergeResults(fixedBottom, fixedTop, inferredTop, inferredBottom);
    }

    private void preprocessConstraints(Set<VariableSlot> fixedBottom,
            Set<VariableSlot> fixedTop,
            Map<VariableSlot, List<VariableSlot>> superTypePropagation,
            Map<VariableSlot, List<VariableSlot>> subTypePropagation) {
        
        for (Constraint constraint: constraints) {
            // Skip constraints that are just constants
            if (!checkConstainsVariable(constraint)) {
                continue;
            }

            if (constraint instanceof EqualityConstraint) {
                EqualityConstraint equality = (EqualityConstraint) constraint;
                if (equality.getFirst() instanceof ConstantSlot) {
                    AnnotationMirror value = ((ConstantSlot) equality.getFirst()).getValue();
                    VariableSlot variable = (VariableSlot) equality.getSecond();
                    if (value.equals(top)) {
                        fixedTop.add(variable);
                    } else {
                        fixedBottom.add(variable);
                    }
                } else if (equality.getSecond() instanceof ConstantSlot) {
                    AnnotationMirror value = ((ConstantSlot) equality.getSecond()).getValue();
                    VariableSlot variable = (VariableSlot) equality.getFirst();
                    if (value.equals(top)) {
                        fixedTop.add(variable);
                    } else {
                        fixedBottom.add(variable);
                    }
                } else {
                    addEntryToMap(superTypePropagation, (VariableSlot) equality.getFirst(), (VariableSlot) equality.getSecond());
                    addEntryToMap(superTypePropagation, (VariableSlot) equality.getSecond(), (VariableSlot) equality.getFirst());
                    addEntryToMap(subTypePropagation, (VariableSlot) equality.getFirst(), (VariableSlot) equality.getSecond());
                    addEntryToMap(subTypePropagation, (VariableSlot) equality.getSecond(), (VariableSlot) equality.getFirst());
                }
            } else if (constraint instanceof SubtypeConstraint) {
                SubtypeConstraint subtype = (SubtypeConstraint) constraint;
                if (subtype.getSubtype() instanceof ConstantSlot) {
                    AnnotationMirror value = ((ConstantSlot) subtype.getSubtype()).getValue();
                    VariableSlot variable = (VariableSlot) subtype.getSupertype();
                    if (value.equals(top)) {
                        fixedTop.add(variable);
                    } 
                } else if (subtype.getSupertype() instanceof ConstantSlot) {
                    AnnotationMirror value = ((ConstantSlot) subtype.getSupertype()).getValue();
                    VariableSlot variable = (VariableSlot) subtype.getSubtype();
                    if (value.equals(bottom)) {
                        fixedBottom.add(variable);
                    }
                } else {
                    addEntryToMap(superTypePropagation, (VariableSlot) subtype.getSubtype(), (VariableSlot) subtype.getSupertype());
                    addEntryToMap(subTypePropagation, (VariableSlot) subtype.getSupertype(), (VariableSlot) subtype.getSubtype());
                }
            }
        }
    }
    
    private Map<Integer, AnnotationMirror> mergeResults(
            Set<VariableSlot> fixedBottom, Set<VariableSlot> fixedTop,
            Set<VariableSlot> inferredTop, Set<VariableSlot> inferredBottom) {
        
        Map<Integer, AnnotationMirror> results = new HashMap<Integer, AnnotationMirror>();
        for (Slot slot : slots) {
            if (slot instanceof VariableSlot) {
                VariableSlot vslot = (VariableSlot) slot;
                if (fixedBottom.contains(slot)) {
                    results.put(vslot.getId(), bottom);
                } else if (fixedTop.contains(slot)) {
                    results.put(vslot.getId(), top);
                } else if (inferredBottom.contains(slot)) {
                    results.put(vslot.getId(), bottom);
                } else if (inferredTop.contains(slot)) {
                    results.put(vslot.getId(), top);
                } else {
                    results.put(vslot.getId(), defaultValue);
                }
            }
        }

        return results;
    }

    private boolean checkConstainsVariable(Constraint constraint) {
        boolean containsVariable = false;
        for (Slot slot : constraint.getSlots()) {
            if (slot instanceof VariableSlot) {
                containsVariable = true;
            }
        }
        return containsVariable;
    }

    private void propagateValues(Set<VariableSlot> fixedTop,
            Map<VariableSlot, List<VariableSlot>> superTypePropagation,
            Set<VariableSlot> inferredTop) {
        
        Set<VariableSlot> worklist = new HashSet<VariableSlot>(fixedTop);
        while (!worklist.isEmpty()) {
            VariableSlot variable = worklist.iterator().next();
            worklist.remove(variable);
            if (superTypePropagation.containsKey(variable)) {
                List<VariableSlot> inferred = superTypePropagation.get(variable);
                inferred.removeAll(inferredTop);
                inferredTop.addAll(inferred);
                worklist.addAll(inferred);
            }
        }
    }

    void addEntryToMap(Map<VariableSlot,List<VariableSlot>> entries, VariableSlot key, VariableSlot value) {
        List<VariableSlot> valueList;
        if (entries.get(key) == null) {
            valueList = new ArrayList<VariableSlot>();
            entries.put(key, valueList);
        } else {
            valueList = entries.get(key);
        }
        valueList.add(value);
    }
}
