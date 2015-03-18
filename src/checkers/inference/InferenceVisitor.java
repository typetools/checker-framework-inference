package checkers.inference;

/*>>>
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
*/

import checkers.inference.model.*;
import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.source.SourceVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeParameterBounds;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.type.VisitorState;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.framework.util.ContractsUtils;
import org.checkerframework.javacutil.TreeUtils;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by jburke on 3/6/15.
 */
public class InferenceVisitor<Checker extends BaseTypeChecker,
        Factory extends GenericAnnotatedTypeFactory<?, ?, ?, ?>>
        extends BaseTypeVisitor<Factory> {

    private static final Logger logger = Logger.getLogger(InferenceVisitor.class.getName());

    /* One design alternative would have been to use two separate subclasses instead of the boolean.
     * However, this separates the inference and checking implementation of a method.
     * Using the boolean, the two implementations are closer together.
     *
     */
    protected final boolean infer;

    protected final Checker realChecker;

    public InferenceVisitor(Checker checker, InferenceChecker ichecker, Factory factory, boolean infer) {
        super((infer) ? ichecker : checker, factory);
        this.realChecker = checker;
        this.infer = infer;
    }

    /* Solely sugar */
    protected void addConstraint(final Constraint constraint) {
        InferenceMain.getInstance().getConstraintManager().add(constraint);
    }

    @Override
    protected Factory createTypeFactory() {
        return (Factory)((BaseInferrableChecker)checker).getTypeFactory();
    }

    @Override
    public boolean isValidUse(AnnotatedDeclaredType declarationType, AnnotatedDeclaredType useType, Tree tree) {
        // TODO at least for the UTS we don't check annotations on the class declaration
        //   println("InferenceChecker::isValidUse: decl: " + declarationType)
        //   println("InferenceChecker::isValidUse: use: " + useType)

        //TODO JB: Currently visitDeclared strips the useType of it's @VarAnnots etc...
        //TODO JB: So the constraints coming from use don't get passed on via visitParameterizedType->checkTypeArguments

        //TODO JB: At the moment this leads to erroneous subtyping between some type parameter elements,
        //TODO JB: Comment this out and visit CalledMethod.java
        return true;
    }


    public void doesNotContain(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        doesNotContain(ty, new AnnotationMirror[] {mod}, msgkey, node);
    }

    public void doesNotContain(AnnotatedTypeMirror ty, AnnotationMirror[] mods, String msgkey, Tree node) {
        if (infer) {
            doesNotContainInfer(ty, mods, node);
        } else {
            for (AnnotationMirror mod : mods) {
                if (AnnotatedTypes.containsModifier(ty, mod)) {
                    checker.report(Result.failure(msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString()), node);
                }
            }
        }
    }

    private void doesNotContainInfer(AnnotatedTypeMirror ty, AnnotationMirror[] mods, Tree node) {
        doesNotContainInferImpl(ty, mods, new java.util.LinkedList<AnnotatedTypeMirror>(), node);
    }

    private void doesNotContainInferImpl(AnnotatedTypeMirror ty, AnnotationMirror[] mods,
                                         java.util.List<AnnotatedTypeMirror> visited, Tree node) {
        if (visited.contains(ty)) {
            return;
        }
        visited.add(ty);

        Slot el = InferenceMain.getInstance().getSlotManager().getSlot(ty);

        if (el == null) {
            // TODO: prims not annotated in UTS, others might
            logger.warning("InferenceVisitor::doesNotContain: no annotation in type: " + ty);
        } else {
            if(! InferenceMain.getInstance().isPerformingFlow()) {
                logger.fine("InferenceVisitor::doesNotContain: Inequality constraint constructor invocation(s).");
            }

            for (AnnotationMirror mod : mods) {
                // TODO: are Constants compared correctly???
                addConstraint(new InequalityConstraint(el, new ConstantSlot(mod)));
            }
        }

        if (ty.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType declaredType = (AnnotatedDeclaredType) ty;
            for (AnnotatedTypeMirror typearg : declaredType.getTypeArguments()) {
                doesNotContainInferImpl(typearg, mods, visited, node);
            }
        } else if (ty.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType arrayType = (AnnotatedArrayType) ty;
            doesNotContainInferImpl(arrayType.getComponentType(), mods, visited, node);
        } else if (ty.getKind() == TypeKind.TYPEVAR) {
            AnnotatedTypeVariable atv = (AnnotatedTypeVariable) ty;
            if (atv.getUpperBound()!=null) {
                doesNotContainInferImpl(atv.getUpperBound(), mods, visited, node);
            }
            if (atv.getLowerBound()!=null) {
                doesNotContainInferImpl(atv.getLowerBound(), mods, visited, node);
            }
        }
    }

    public void mainIs(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        if (infer) {
            Slot el = InferenceMain.getInstance().getSlotManager().getSlot(ty);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::mainIs: no annotation in type: " + ty);
            } else {
                if(!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIs: Equality constraint constructor invocation(s).");
                    addConstraint(new EqualityConstraint(el, new ConstantSlot(mod)));
                }
            }
        } else {
            if (!ty.hasEffectiveAnnotation(mod)) {
                checker.report(Result.failure(msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString()), node);
            }
        }
    }

    public void mainIsSubtype(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        if (infer) {
            Slot el = InferenceMain.getInstance().getSlotManager().getSlot(ty);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::mainIs: no annotation in type: " + ty);
            } else {
                if(!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIs: Subtype constraint constructor invocation(s).");
                    addConstraint(new SubtypeConstraint(el, new ConstantSlot(mod)));
                }
            }
        } else {
            if (!ty.hasEffectiveAnnotation(mod)) {
                checker.report(Result.failure(msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString()), node);
            }
        }
    }

    public void mainIsNot(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        mainIsNoneOf(ty, new AnnotationMirror[] {mod}, msgkey, node);
    }

    public void mainIsNoneOf(AnnotatedTypeMirror ty, AnnotationMirror[] mods, String msgkey, Tree node) {
        if (infer) {
            Slot el = InferenceMain.getInstance().getSlotManager().getSlot(ty);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::isNoneOf: no annotation in type: " + ty);
            } else {
                if( !InferenceMain.getInstance().isPerformingFlow() ) {
                    logger.fine("InferenceVisitor::mainIsNoneOf: Inequality constraint constructor invocation(s).");

                    for (AnnotationMirror mod : mods) {
                        addConstraint(new InequalityConstraint(el, new ConstantSlot(mod)));
                    }
                }
            }
        } else {
            for (AnnotationMirror mod : mods) {
                if (ty.hasEffectiveAnnotation(mod)) {
                    checker.report(Result.failure(msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString()), node);
                }
            }
        }
    }



    public void areComparable(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el1 = slotManager.getSlot(ty1);
            Slot el2 = slotManager.getSlot(ty2);

            if (el1 == null || el2 == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::areComparable: no annotation on type: " + ty1 + " or " + ty2);
            } else {
                if( !InferenceMain.getInstance().isPerformingFlow() ) {
                    logger.fine("InferenceVisitor::areComparable: Comparable constraint constructor invocation.");
                    addConstraint(new ComparableConstraint(el1, el2));
                }
            }
        } else {
            if (!(atypeFactory.getTypeHierarchy().isSubtype(ty1, ty2) || atypeFactory.getTypeHierarchy().isSubtype(ty2, ty1))) {
                checker.report(Result.failure(msgkey, ty1.toString(), ty2.toString(), node.toString()), node);
            }
        }
    }

    public void areEqual(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el1 = slotManager.getSlot(ty1);
            Slot el2 = slotManager.getSlot(ty2);

            if (el1 == null || el2 == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::areEqual: no annotation on type: " + ty1 + " or " + ty2);
            } else {
                if( !InferenceMain.getInstance().isPerformingFlow() ) {
                    logger.fine("InferenceVisitor::areEqual: Equality constraint constructor invocation.");
                    addConstraint(new EqualityConstraint(el1, el2));
                }
            }
        } else {
            if (!ty1.equals(ty2)) {
                checker.report(Result.failure(msgkey, ty1.toString(), ty2.toString(), node.toString()), node);
            }
        }
    }

    @Override
    public void checkTypeArguments(Tree toptree,
                                   List<? extends AnnotatedTypeParameterBounds> typevars,
                                   List<? extends AnnotatedTypeMirror> typeargs,
                                   List<? extends Tree> typeargTrees) {
        //see repository history for previous comment out hacky implementation
    }

    @Override
    protected boolean checkConstructorInvocation(AnnotatedDeclaredType dt,
                                                 AnnotatedExecutableType constructor, Tree src) {

        AnnotatedDeclaredType receiver = constructor.getReceiverType();
        // Only constructors for nested classes have a receiver
        if (receiver != null) {
            areComparable(dt, receiver, "constructor.invocation.invalid", src);
        } //TODO_JB: CHECK AGAINST THE FIX I MADE FOR INTERFACE AND IS_OBJECT (SEE SUPER METHOD)


        return true;
    }
    /**
     * Checks the validity of an assignment (or pseudo-assignment) from a value
     * to a variable and emits an error message (through the compiler's
     * messaging interface) if it is not valid.
     *
     * @param varTree the AST node for the variable
     * @param valueExp the AST node for the value
     * @param errorKey the error message to use if the check fails (must be a
     *        compiler message key, see {@link org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey})
     */
    @Override
    protected void commonAssignmentCheck(Tree varTree, ExpressionTree valueExp,
            /*@CompilerMessageKey*/ String errorKey) {
        if (!validateTypeOf(varTree)) {
            return;
        }

        // commonAssignmentCheck eventually create an equality constraint between varTree and valueExp.
        // For inference, we need this constraint to be between the RefinementVariable and the value.
        // Refinement variables come from flow inference, so we need to call getAnnotatedType instead of getDefaultedAnnotatedType
        AnnotatedTypeMirror var;
        if (this.infer) {
            var = atypeFactory.getAnnotatedType(varTree);
        } else {
            var = atypeFactory.getDefaultedAnnotatedType(varTree, valueExp);
        }

        assert var != null : "no variable found for tree: " + varTree;

        checkAssignability(var, varTree);

        boolean isLocalVariableAssignment = false;
        if (varTree instanceof AssignmentTree) {
            Tree rhs = ((AssignmentTree) varTree).getVariable();
            isLocalVariableAssignment = rhs instanceof IdentifierTree
                    && !TreeUtils.isFieldAccess(rhs);
        }
        if (varTree instanceof VariableTree) {
            isLocalVariableAssignment = TreeUtils.enclosingMethod(getCurrentPath()) != null;
        }

        commonAssignmentCheck(var, valueExp, errorKey,
                isLocalVariableAssignment);
    }

    @Override
    protected void commonAssignmentCheck(AnnotatedTypeMirror varType,
                                         AnnotatedTypeMirror valueType, Tree valueTree, /*@CompilerMessageKey*/ String errorKey,
                                         boolean isLocalVariableAssignement) {

        String valueTypeString = valueType.toString();
        String varTypeString = varType.toString();

        // If both types as strings are the same, try outputting
        // the type including also invisible qualifiers.
        // This usually means there is a mistake in type defaulting.
        // This code is therefore not covered by a test.
        if (valueTypeString.equals(varTypeString)) {
            valueTypeString = valueType.toString(true);
            varTypeString = varType.toString(true);
        }

        if (isLocalVariableAssignement && varType.getKind() == TypeKind.TYPEVAR
                && varType.getAnnotations().isEmpty()) {
            // If we have an unbound local variable that is a type variable,
            // then we allow the assignment.
            return;
        }

        if (checker.hasOption("showchecks")) {
            long valuePos = positions.getStartPosition(root, valueTree);
            System.out.printf(
                    " %s (line %3d): %s %s%n     actual: %s %s%n   expected: %s %s%n",
                    "About to test whether actual is a subtype of expected",
                    (root.getLineMap() != null ? root.getLineMap().getLineNumber(valuePos) : -1),
                    valueTree.getKind(), valueTree,
                    valueType.getKind(), valueTypeString,
                    varType.getKind(), varTypeString);
        }

        // Handle refinement variables.
        // If this is the result of an assignment,
        // instead of a subtype relationship we know the refinement variable
        // on the LHS must be equal to the variable on the RHS.
        boolean success = true;
        boolean inferenceRefinementVariable = false;
        if (infer) {
            Slot sup = InferenceMain.getInstance().getSlotManager().getSlot(varType);
            if (sup instanceof RefinementVariableSlot && !InferenceMain.getInstance().isPerformingFlow()) {
                inferenceRefinementVariable = true;
                Slot sub = InferenceMain.getInstance().getSlotManager().getSlot(valueType);
                logger.fine("InferenceVisitor::commonAssignmentCheck: Equality constraint for qualifiers sub: " + sub + " sup: " + sup);

                // Equality between the refvar and the value
                InferenceMain.getInstance().getConstraintManager().add(new EqualityConstraint(sup, sub));

                // Refinement variable still needs to be a subtype of its declared type value
                InferenceMain.getInstance().getConstraintManager().add(new SubtypeConstraint(sup, ((RefinementVariableSlot) sup).getRefined()));
            }
        }

        if (!inferenceRefinementVariable) {
            success = atypeFactory.getTypeHierarchy().isSubtype(valueType, varType);
        }

        // TODO: integrate with subtype test.
        if (success) {
            for (Class<? extends Annotation> mono : atypeFactory.getSupportedMonotonicTypeQualifiers()) {
                if (valueType.hasAnnotation(mono)
                        && varType.hasAnnotation(mono)) {
                    checker.report(
                            Result.failure("monotonic.type.incompatible",
                                    mono.getCanonicalName(),
                                    mono.getCanonicalName(),
                                    valueType.toString()), valueTree);
                    return;
                }
            }
        }

        if (checker.hasOption("showchecks")) {
            long valuePos = positions.getStartPosition(root, valueTree);
            System.out.printf(
                    " %s (line %3d): %s %s%n     actual: %s %s%n   expected: %s %s%n",
                    (success ? "success: actual is subtype of expected" : "FAILURE: actual is not subtype of expected"),
                    (root.getLineMap() != null ? root.getLineMap().getLineNumber(valuePos) : -1),
                    valueTree.getKind(), valueTree,
                    valueType.getKind(), valueTypeString,
                    varType.getKind(), varTypeString);
        }

        // Use an error key only if it's overridden by a checker.
        if (!success) {
            checker.report(Result.failure(errorKey,
                    valueTypeString, varTypeString), valueTree);
        }
    }

    //TODO: WE NEED TO FIX this method and have it do something sensible
    //TODO: The issue here is that I have removed the error reporting from this method
    //TODO: In order to allow verigames to move forward.
    /**
     * Tests whether the tree expressed by the passed type tree is a valid type,
     * and emits an error if that is not the case (e.g. '@Mutable String').
     * If the tree is a method or constructor, check the return type.
     *
     * @param tree  the AST type supplied by the user
     */
    public boolean validateTypeOf(Tree tree) {
        AnnotatedTypeMirror type;
        // It's quite annoying that there is no TypeTree
        switch (tree.getKind()) {
            case PRIMITIVE_TYPE:
            case PARAMETERIZED_TYPE:
            case TYPE_PARAMETER:
            case ARRAY_TYPE:
            case UNBOUNDED_WILDCARD:
            case EXTENDS_WILDCARD:
            case SUPER_WILDCARD:
            case ANNOTATED_TYPE:
                type = atypeFactory.getAnnotatedTypeFromTypeTree(tree);
                break;
            case METHOD:
                type = atypeFactory.getMethodReturnType((MethodTree) tree);
                if (type == null ||
                        type.getKind() == TypeKind.VOID) {
                    // Nothing to do for void methods.
                    // Note that for a constructor the AnnotatedExecutableType does
                    // not use void as return type.
                    return true;
                }
                break;
            default:
                type = atypeFactory.getAnnotatedType(tree);
        }

        // basic consistency checks
        if (!AnnotatedTypes.isValidType(atypeFactory.getQualifierHierarchy(), type)) {
//            checker.report(Result.failure("type.invalid", type.getAnnotations(),
//                    type.toString()), tree);
//            return false;
            return true;
        }

        //TODO: THIS MIGHT FAIL
//        typeValidator.isValid(type, tree);
        // more checks (also specific to checker, potentially)
        return true;
    }

    protected InferenceValidator createTypeValidator() {
        return new InferenceValidator(checker, this, atypeFactory);
    }
}
