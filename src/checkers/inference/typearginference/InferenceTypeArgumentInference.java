package checkers.inference.typearginference;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.util.typeinference.DefaultTypeArgumentInference;
import org.checkerframework.framework.util.typeinference.TypeArgInferenceUtil;
import org.checkerframework.framework.util.typeinference.constraint.A2F;
import org.checkerframework.framework.util.typeinference.constraint.AFConstraint;
import org.checkerframework.framework.util.typeinference.constraint.F2A;
import org.checkerframework.framework.util.typeinference.constraint.TIsU;
import org.checkerframework.framework.util.typeinference.constraint.TSubU;
import org.checkerframework.framework.util.typeinference.constraint.TSuperU;
import org.checkerframework.framework.util.typeinference.constraint.TUConstraint;
import org.checkerframework.javacutil.Pair;

import static org.checkerframework.framework.util.AnnotatedTypes.findEffectiveAnnotationInHierarchy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;

import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.InferenceTypeHierarchy;
import checkers.inference.SlotManager;
import checkers.inference.VariableAnnotator;
import checkers.inference.VariableSlotReplacer;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.CrossFactoryAtmCopier;
import checkers.inference.util.InferenceUtil;

/**
 *
 * The basic algorithm for type argument inference for Checker Framework Inference is as follows:
 *     1) Infer the unqualified Java types for each type argument
 *
 *     2) Apply annotations to each Java type inferred in step 1
 *     If an inferred Java type is a type variable then it should be annotated with ExistentialVariables
 *     just like any other type variable.
 *
 *     3) Extract the primary variables that were applied in step 2 for use in steps 4+
 *
 *     4) For each use of a target type variable (i.e. one that we are inferring) in the
 *     formal parameter list, replace any ExistentialVariables that involve the bounds of the type variable
 *     to use the annotations from step 3 instead.
 *
 *     5) Compute TUConstraints using the standard type argument inference algorithm found in
 *     DefaultTypeArgumentInference.
 *
 *     6) Add the constraints between the types inferred in step 1 and the corresponding type parameter's
 *     bounds.
 *
 *     7) Convert the TUConstraints from step 5 into Checker Framework Inference Constraints
 */
public class InferenceTypeArgumentInference extends DefaultTypeArgumentInference {

    private final AnnotatedTypeFactory realTypeFactory;
    private final InferenceAnnotatedTypeFactory inferenceTypeFactory;
    private final ConstraintManager constraintManager;
    private final VariableAnnotator variableAnnotator;
    private final AnnotationMirror varAnnot;
    private final SlotManager slotManager;

    public InferenceTypeArgumentInference(SlotManager slotManager,
                                          ConstraintManager constraintManager,
                                          VariableAnnotator variableAnnotator,
                                          InferenceAnnotatedTypeFactory inferenceTypeFactory,
                                          AnnotatedTypeFactory realTypeFactory,
                                          AnnotationMirror varAnnot) {
        super(realTypeFactory);
        this.slotManager = slotManager;
        this.constraintManager = constraintManager;
        this.variableAnnotator = variableAnnotator;
        this.inferenceTypeFactory = inferenceTypeFactory;
        this.realTypeFactory = realTypeFactory;
        this.varAnnot = varAnnot;
    }

    @Override
    public Map<TypeVariable, AnnotatedTypeMirror> inferTypeArgs(AnnotatedTypeFactory typeFactory,
                                                                ExpressionTree expressionTree,
                                                                ExecutableElement methodElem,
                                                                AnnotatedExecutableType methodType) {
        //we don't want any lubs etc. used in inferreing types to generate constraints
        constraintManager.startIgnoringConstraints();

        List<AnnotatedTypeMirror> targetTypes = getUnannotatedTypeArgs(expressionTree);

        for (AnnotatedTypeMirror targetType : targetTypes) {
            variableAnnotator.annotateImpliedType(targetType, true, null);
        }

        final Set<TypeVariable> targets = TypeArgInferenceUtil.methodTypeToTargets(methodType);

        Map<TypeVariable, AnnotatedTypeMirror> targetToType = InferenceUtil.makeOrderedMap(targets, targetTypes);
        Map<TypeVariable, VariableSlot> targetToPrimary = findTargetVariableSlots(targetToType);

        final List<AnnotatedTypeMirror> argTypes = TypeArgInferenceUtil.getArgumentTypes(expressionTree, typeFactory);
        final AnnotatedTypeMirror assignedTo =
                TypeArgInferenceUtil.assignedTo(typeFactory, typeFactory.getPath(expressionTree));
        final AnnotatedExecutableType updatedMethod = methodType.deepCopy();
        replaceExistentialVariables(updatedMethod, typeFactory, targetToPrimary);

        Set<TUConstraint> tuConstraints = createTUConstraints(argTypes, assignedTo, updatedMethod, targetToType, typeFactory);

        constraintManager.stopIgnoringConstraints();

        recordConstraints(tuConstraints);
        return targetToType;
    }

    private Set<TUConstraint> createTUConstraints(List<AnnotatedTypeMirror> argTypes, AnnotatedTypeMirror assignedTo,
                                                  AnnotatedExecutableType methodType,
                                                  Map<TypeVariable, AnnotatedTypeMirror> targetToTypes,
                                                  AnnotatedTypeFactory typeFactory) {

        Set<TypeVariable> targets = targetToTypes.keySet();
        Set<AFConstraint> reducedConstraints = createArgumentAFConstraints(typeFactory, argTypes,
                methodType, targets, true);
        reducedConstraints.addAll(createBoundAndAssignmentAFs(assignedTo, methodType, targetToTypes, typeFactory));

        Set<TUConstraint> tuConstraints = afToTuConstraints(reducedConstraints, targets);
        addConstraintsBetweenTargets(tuConstraints, targets, false, typeFactory);

        return tuConstraints;
    }

    private Set<AFConstraint> createBoundAndAssignmentAFs(AnnotatedTypeMirror assignedTo,
                                                           AnnotatedExecutableType methodType,
                                                           Map<TypeVariable, AnnotatedTypeMirror> targetToTypes,
                                                           AnnotatedTypeFactory typeFactory) {

        final LinkedList<AFConstraint> boundAndAssignmentAfs = new LinkedList<>();
        for (AnnotatedTypeVariable typeParam : methodType.getTypeVariables()) {
            final TypeVariable target = typeParam.getUnderlyingType();
            final AnnotatedTypeMirror inferredType = targetToTypes.get(target);
            //for all inferred types Ti:  Ti >> Bi where Bi is upper bound and Ti << Li where Li is the lower bound
            //for all uninferred types Tu: Tu >> Bi and Lu >> Tu
            if (inferredType != null) {
                boundAndAssignmentAfs.add(new A2F(inferredType, typeParam.getUpperBound()));
                boundAndAssignmentAfs.add(new F2A(typeParam.getLowerBound(), inferredType));
            } else {
                boundAndAssignmentAfs.add(new F2A(typeParam, typeParam.getUpperBound()));
                boundAndAssignmentAfs.add(new A2F(typeParam.getLowerBound(), typeParam));
            }
        }

        final AnnotatedTypeMirror declaredReturnType = methodType.getReturnType();
        if (declaredReturnType.getKind() != TypeKind.VOID) {
            final AnnotatedTypeMirror boxedReturnType;
            if (declaredReturnType.getKind().isPrimitive()) {
                boxedReturnType = typeFactory.getBoxedType((AnnotatedPrimitiveType) declaredReturnType);
            } else {
                boxedReturnType = declaredReturnType;
            }

            boundAndAssignmentAfs.add(new F2A(boxedReturnType, assignedTo));
        }

        Set<AFConstraint> reducedConstraints = new LinkedHashSet<>();
        reduceAfConstraints(typeFactory, reducedConstraints, boundAndAssignmentAfs, targetToTypes.keySet());
        return reducedConstraints;
    }


    //TODO: Figure out if we need to make copies of the types
    private void replaceExistentialVariables(AnnotatedExecutableType methodType, AnnotatedTypeFactory typeFactory,
                                             Map<TypeVariable, VariableSlot> targetToPrimary) {
        VariableSlotReplacer variableSlotReplacer = new VariableSlotReplacer(slotManager, variableAnnotator,
                                                                             varAnnot, true);

        for (TypeVariable target : targetToPrimary.keySet()) {
            AnnotatedTypeVariable typeVariable = (AnnotatedTypeVariable) typeFactory.getAnnotatedType(target.asElement());

            AnnotatedTypeMirror upperBound = typeVariable.getUpperBound();
            AnnotatedTypeMirror lowerBound = typeVariable.getLowerBound();

            AnnotationMirror upperBoundAnno =
                findEffectiveAnnotationInHierarchy(inferenceTypeFactory.getQualifierHierarchy(), upperBound, varAnnot);
            VariableSlot upperBoundVariable = (VariableSlot) slotManager.getSlot(upperBoundAnno);

            //handles the cases like <T, E extends T>, the upper bound anno on E will appear as a potential
            //annotation on T
            if (upperBoundVariable instanceof ExistentialVariableSlot) {
                upperBoundVariable  = ((ExistentialVariableSlot) upperBoundVariable).getPotentialSlot();
            }

            VariableSlot lowerBoundVariable = slotManager.getVariableSlot(lowerBound);

            VariableSlot newSlot = targetToPrimary.get(target);
            variableSlotReplacer.addReplacement(upperBoundVariable, newSlot);
            variableSlotReplacer.addReplacement(lowerBoundVariable, newSlot);
        }

        for (AnnotatedTypeMirror type : methodType.getParameterTypes()) {
            variableSlotReplacer.replaceSlots(type);
        }

        AnnotatedTypeMirror returnType = methodType.getReturnType();
        if (returnType.getKind() != TypeKind.VOID) {
            variableSlotReplacer.replaceSlots(returnType);
        }

    }

    private Map<TypeVariable, VariableSlot> findTargetVariableSlots(Map<TypeVariable, AnnotatedTypeMirror> targetToType) {

        Map<TypeVariable, VariableSlot> targetToVar = new LinkedHashMap<>();

        for (TypeVariable target : targetToType.keySet()) {
            final AnnotatedTypeMirror type = targetToType.get(target);

            AnnotationMirror variableAnno;
            switch (type.getKind()) {
                case TYPEVAR:
                case WILDCARD:
                    variableAnno = findEffectiveAnnotationInHierarchy(inferenceTypeFactory.getQualifierHierarchy(),
                                                                      type, varAnnot);
                    break;

                default:
                    variableAnno = type.getAnnotationInHierarchy(varAnnot);
            }

            VariableSlot variable = (VariableSlot) slotManager.getSlot(variableAnno);
            if (variable instanceof ExistentialVariableSlot) {
                targetToVar.put(target, ((ExistentialVariableSlot) variable).getPotentialSlot());

            } else {
                targetToVar.put(target, variable);

            }
        }


        return targetToVar;
    }

    /**
     * Use the real type factory's viewpoint adaptation to obtain the Java types of the type arguments
     * as AnnotatedTypeMirrors.  Copy them using the InferenceAnnotatedTypeFactory and return AnnotatedTypeMirrors
     * that contain NO annotations.
     *
     * @param expressionTree A MethodInvocationTree or a ConstructorInvocationTree
     * @return
     */
    private List<AnnotatedTypeMirror> getUnannotatedTypeArgs(ExpressionTree expressionTree) {

        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> fromUseResult;

        switch (expressionTree.getKind()) {
            case METHOD_INVOCATION:
                fromUseResult = realTypeFactory.methodFromUse((MethodInvocationTree) expressionTree);
                break;

            case NEW_CLASS:
                fromUseResult = realTypeFactory.constructorFromUse((NewClassTree) expressionTree);
                break;

            default:
                throw new IllegalArgumentException("expressionTree should be a MethodInvocation or NewClass tree\n"
                                                 + "expressionTree=" + expressionTree);
        }

        List<AnnotatedTypeMirror> emptyAnnotatedTypeMirrors = new ArrayList<>(fromUseResult.second.size());
        for (AnnotatedTypeMirror realAnnotatedType : fromUseResult.second) {
            AnnotatedTypeMirror inferenceType = CrossFactoryAtmCopier.copy(realAnnotatedType, inferenceTypeFactory, false);
            emptyAnnotatedTypeMirrors.add(inferenceType);
        }

        return emptyAnnotatedTypeMirrors;
    }


    private void recordConstraints(Set<TUConstraint> tuConstraints) {
        InferenceTypeHierarchy inferenceTypeHierarchy = (InferenceTypeHierarchy) inferenceTypeFactory.getTypeHierarchy();
        for (TUConstraint tuConstraint : tuConstraints) {
            if (tuConstraint instanceof TSubU) {
                inferenceTypeHierarchy.isSubtype(tuConstraint.typeVariable, tuConstraint.relatedType);

            } else if (tuConstraint instanceof TSuperU) {
                inferenceTypeHierarchy.isSubtype(tuConstraint.relatedType, tuConstraint.typeVariable);

            } else if (tuConstraint instanceof TIsU) {
                inferenceTypeHierarchy.areEqual(tuConstraint.relatedType, tuConstraint.typeVariable);

            }
        }
    }
}
