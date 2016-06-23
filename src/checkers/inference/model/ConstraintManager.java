package checkers.inference.model;

import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.VisitorState;
import org.checkerframework.javacutil.AnnotationUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.VariableAnnotator;

/**
 * Constraint manager holds constraints that are generated by InferenceVisitor.
 *
 * @author mcarthur
 *
 */
public class ConstraintManager {

    // TODO:
    private boolean ignoreConstraints = false;

    private final Set<Constraint> constraints = new HashSet<Constraint>();

    private InferenceAnnotatedTypeFactory inferenceTypeFactory;

    private SourceChecker checker;

    private QualifierHierarchy qualHierarchy;

    private VisitorState visitorState;

    public void init(InferenceAnnotatedTypeFactory realTypeFactory) {
        this.inferenceTypeFactory = realTypeFactory;
        this.qualHierarchy = realTypeFactory.getQualifierHierarchy();
        this.visitorState = realTypeFactory.getVisitorState();
        this.checker = realTypeFactory.getContext().getChecker();
    }

    public Set<Constraint> getConstraints() {
        return constraints;
    }

    private void add(Constraint constraint) {
        if (!ignoreConstraints) {
            constraints.add(constraint);
        }
    }

    public void startIgnoringConstraints() {
        this.ignoreConstraints = true;
    }

    public void stopIgnoringConstraints() {
        this.ignoreConstraints = false;
    }

    public SubtypeConstraint createSubtypeConstraint(Slot subtype, Slot supertype) {
        if (subtype instanceof ConstantSlot && supertype instanceof ConstantSlot) {
            ConstantSlot subConstant = (ConstantSlot) subtype;
            ConstantSlot superConstant = (ConstantSlot) supertype;

            if (!qualHierarchy.isSubtype(subConstant.getValue(), superConstant.getValue())) {
                checker.report(Result.failure("subtype.type.incompatible", subtype, supertype),
                        visitorState.getPath().getLeaf());
            }
        }
        SubtypeConstraint subtypeConstraint = new SubtypeConstraint(subtype, supertype);
        setLocation(subtypeConstraint);
        return subtypeConstraint;
    }

    public EqualityConstraint createEqualityConstraint(Slot first, Slot second) {
        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConstant = (ConstantSlot) first;
            ConstantSlot secondConstant = (ConstantSlot) second;
            if (!areSameType(firstConstant.getValue(), secondConstant.getValue())) {
                checker.report(Result.failure("equality.type.incompatible", first, second), visitorState
                        .getPath().getLeaf());
            }
        }
        EqualityConstraint equalityConstraint = new EqualityConstraint(first, second);
        setLocation(equalityConstraint);
        return equalityConstraint;
    }

    public InequalityConstraint createInequalityConstraint(Slot first, Slot second) {
        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConstant = (ConstantSlot) first;
            ConstantSlot secondConstant = (ConstantSlot) second;
            if (areSameType(firstConstant.getValue(), secondConstant.getValue())) {
                checker.report(Result.failure("inequality.type.incompatible", first, second),
                        visitorState.getPath().getLeaf());
            }
        }
        InequalityConstraint inequalityConstraint = new InequalityConstraint(first, second);
        setLocation(inequalityConstraint);
        return inequalityConstraint;
    }

    public ComparableConstraint createComparableConstraint(Slot first, Slot second) {
        if (first instanceof ConstantSlot && second instanceof ConstantSlot) {
            ConstantSlot firstConstant = (ConstantSlot) first;
            ConstantSlot secondConstant = (ConstantSlot) second;
            if (!qualHierarchy.isSubtype(firstConstant.getValue(), secondConstant.getValue())
                    && !qualHierarchy.isSubtype(secondConstant.getValue(), firstConstant.getValue())) {
                checker.report(Result.failure("comparable.type.incompatible", first, second),
                        visitorState.getPath().getLeaf());
            }
        }
        ComparableConstraint comparableConstraint = new ComparableConstraint(first, second);
        setLocation(comparableConstraint);
        return comparableConstraint;
    }

    public CombineConstraint createCombineConstraint(Slot target, Slot decl, Slot result) {
        CombineConstraint combineConstraint = new CombineConstraint(target, decl, result);
        setLocation(combineConstraint);
        return combineConstraint;
    }

    public PreferenceConstraint createPreferenceConstraint(VariableSlot variable, ConstantSlot goal,
            int weight) {

        PreferenceConstraint preferenceConstraint = new PreferenceConstraint(variable, goal, weight);
        setLocation(preferenceConstraint);
        return preferenceConstraint;
    }

    public ExistentialConstraint createExistentialConstraint(Slot slot,
            List<Constraint> ifExistsConstraints, List<Constraint> ifNotExistsConstraints) {
        ExistentialConstraint existentialConstraint = new ExistentialConstraint((VariableSlot) slot,
                ifExistsConstraints, ifNotExistsConstraints);
        setLocation(existentialConstraint);
        return existentialConstraint;
    }

    private void setLocation(Constraint constraint) {
        if (visitorState.getPath() != null) {
            constraint.setLocation(VariableAnnotator.treeToLocation(inferenceTypeFactory, visitorState
                    .getPath().getLeaf()));
        }
        // TODO: visitorState should never be null
    }

    public void addSubtypeConstraint(Slot subtype, Slot supertype) {
        if ((subtype instanceof ConstantSlot)
                && this.qualHierarchy.getTopAnnotations().contains(((ConstantSlot) subtype).getValue())) {
            this.addEqualityConstraint(supertype, (ConstantSlot) subtype);
        } else if ((supertype instanceof ConstantSlot)
                && this.qualHierarchy.getBottomAnnotations().contains(
                        ((ConstantSlot) supertype).getValue())) {
            this.addEqualityConstraint(subtype, (ConstantSlot) supertype);
        } else {
            this.add(this.createSubtypeConstraint(subtype, supertype));
        }
    }

    public void addEqualityConstraint(Slot first, Slot second) {
        this.add(this.createEqualityConstraint(first, second));
    }

    public void addInequalityConstraint(Slot first, Slot second) {
        this.add(this.createInequalityConstraint(first, second));
    }

    public void addComparableConstraint(Slot first, Slot second) {
        this.add(this.createComparableConstraint(first, second));
    }

    public void addCombineConstraint(Slot target, Slot decl, Slot result) {
        this.add(this.createCombineConstraint(target, decl, result));
    }

    public void addPreferenceConstraint(VariableSlot variable, ConstantSlot goal, int weight) {
        this.add(this.createPreferenceConstraint(variable, goal, weight));
    }

    public void addExistentialConstraint(Slot slot, List<Constraint> ifExistsConstraints,
            List<Constraint> ifNotExistsConstraints) {
        this.add(this.createExistentialConstraint(slot, ifExistsConstraints, ifNotExistsConstraints));
    }

    private boolean areSameType(AnnotationMirror m1, AnnotationMirror m2) {
        return AnnotationUtils.areSameIgnoringValues(m1, m2);
    }
}
