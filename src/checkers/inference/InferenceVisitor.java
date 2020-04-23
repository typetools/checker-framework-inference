package checkers.inference;

import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.common.subtyping.qual.Unqualified;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedUnionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.AnnotatedTypeParameterBounds;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.InferenceUtil;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;


/**
 *  InferenceVisitor visits trees in each compilation unit both in typecheck/inference mode.
 *  In typecheck mode, it functions nearly identically to BaseTypeVisitor, i.e. it
 *  enforces common assignment and other checks.  However, it also defines a new
 *  API that may be more intuitive for checker writers (see mainIsNot).
 *
 *  InferneceVisitor has an "infer" flag which indicates whether or not
 *  it is in typecheck or in inference mode.  When true, this class replaces type checks
 *  with constraint generation.
 *
 *  InferneceVisitor is intended to replace BaseTypeVisitor.
 *  That is, the methods from BaseTypeVisiotr should be migrated here and InferenceVisitor
 *  should replace it in the Visitor hierarchy.
 */
public class InferenceVisitor<Checker extends InferenceChecker,
        Factory extends BaseAnnotatedTypeFactory>
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
        ((InferenceValidator)typeValidator).setInfer(infer);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Factory createTypeFactory() {
        return (Factory)((BaseInferrableChecker)checker).getTypeFactory();
    }

    public boolean isValidUse(final AnnotatedDeclaredType declarationType,
                              final AnnotatedDeclaredType useType) {
        // TODO at least for the UTS we don't check annotations on the class declaration
        //   println("InferenceChecker::isValidUse: decl: " + declarationType)
        //   println("InferenceChecker::isValidUse: use: " + useType)

        // TODO JB: Currently visitDeclared strips the useType of it's @VarAnnots etc...
        // TODO JB: So the constraints coming from use don't get passed on via visitParameterizedType->checkTypeArguments

        // TODO JB: At the moment this leads to erroneous subtyping between some type parameter elements,
        // TODO JB: Comment this out and visit CalledMethod.java
        return atypeFactory.getTypeHierarchy().isSubtype(useType.getErased(), declarationType.getErased());
        // return true;
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
                    checker.reportError(node, msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString());
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

        final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
        Slot el = slotManager.getVariableSlot(ty);

        if (el == null) {
            // TODO: prims not annotated in UTS, others might
            logger.warning("InferenceVisitor::doesNotContain: no annotation in type: " + ty);
        } else {
            if (!InferenceMain.getInstance().isPerformingFlow()) {
                logger.fine("InferenceVisitor::doesNotContain: Inequality constraint constructor invocation(s).");
            }

            ConstraintManager cm = InferenceMain.getInstance().getConstraintManager();
            for (AnnotationMirror mod : mods) {
                // TODO: are Constants compared correctly???
                cm.addInequalityConstraint(el, slotManager.getSlot(mod));
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

    private AnnotationMirror findEffectiveAnnotation(AnnotatedTypeMirror type, AnnotationMirror target) {
        if (infer) {
            AnnotationMirror varAnnot = ((InferenceAnnotatedTypeFactory) atypeFactory).getVarAnnot();
            return AnnotatedTypes.findEffectiveAnnotationInHierarchy(atypeFactory.getQualifierHierarchy(), type,
                    varAnnot, InferenceMain.isHackMode());
        }

        return AnnotatedTypes.findEffectiveAnnotationInHierarchy(atypeFactory.getQualifierHierarchy(), type, target,
                InferenceMain.isHackMode());
    }

    public void effectiveIs(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        AnnotationMirror effective = findEffectiveAnnotation(ty, mod);
        if (InferenceMain.isHackMode(effective == null)) {
            return;
        }

        annoIs(ty, effective, mod, msgkey, node);
    }

    public void effectiveIsNot(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        AnnotationMirror effective = findEffectiveAnnotation(ty, mod);
        annoIsNot(ty, effective, mod, msgkey, node);
    }

    public void mainIs(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        annoIs(ty, ty.getAnnotationInHierarchy(mod), mod, msgkey, node);
    }

    public void mainIsSubtype(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        if (infer) {

            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el = slotManager.getVariableSlot(ty);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::mainIs: no annotation in type: " + ty);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIs: Subtype constraint constructor invocation(s).");
                    InferenceMain.getInstance().getConstraintManager().addSubtypeConstraint(el, slotManager.getSlot(mod));
                }
            }
        } else {
            if (!ty.hasEffectiveAnnotation(mod)) {
                checker.reportError(node, msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString());
            }
        }
    }

    public void mainIsNot(AnnotatedTypeMirror ty, AnnotationMirror mod, String msgkey, Tree node) {
        mainIsNoneOf(ty, new AnnotationMirror[] {mod}, msgkey, node);
    }

    public void mainIsNoneOf(AnnotatedTypeMirror ty, AnnotationMirror[] mods, String msgkey, Tree node) {
        if (infer) {

            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el = slotManager.getVariableSlot(ty);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::isNoneOf: no annotation in type: " + ty);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIsNoneOf: Inequality constraint constructor invocation(s).");

                    for (AnnotationMirror mod : mods) {
                        InferenceMain.getInstance().getConstraintManager().addInequalityConstraint(el, slotManager.getSlot(mod));
                    }
                }
            }
        } else {
            for (AnnotationMirror mod : mods) {
                if (ty.hasEffectiveAnnotation(mod)) {
                    checker.reportError(node, msgkey, ty.getAnnotations().toString(), ty.toString(), node.toString());
                }
            }
        }
    }

    public void addPreference(AnnotatedTypeMirror type, AnnotationMirror anno, int weight) {
        if (infer) {
            ConstraintManager cManager = InferenceMain.getInstance().getConstraintManager();
            SlotManager sManager = InferenceMain.getInstance().getSlotManager();
            VariableSlot vSlot = sManager.getVariableSlot(type);
            ConstantSlot cSlot = InferenceMain.getInstance().getSlotManager().createConstantSlot(anno);
            cManager.addPreferenceConstraint(vSlot, cSlot, weight);
        }
        // Nothing to do in type check mode.
    }

    protected void annoIs(AnnotatedTypeMirror sourceType, AnnotationMirror effectiveAnno, AnnotationMirror target, String msgKey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el = slotManager.getSlot(effectiveAnno);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::mainIs: no annotation in type: " + sourceType);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIs: Equality constraint constructor invocation(s).");
                    InferenceMain.getInstance().getConstraintManager()
                            .addEqualityConstraint(el, slotManager.getSlot(target));
                }
            }
        } else {
            if (!AnnotationUtils.areSame(effectiveAnno, target)) {
                checker.reportError(node, msgKey, effectiveAnno, sourceType.toString(), node.toString());
            }
        }
    }

    protected void annoIsNot(AnnotatedTypeMirror sourceType, AnnotationMirror effectiveAnno, AnnotationMirror target,
                             String msgKey, Tree node) {
        annoIsNoneOf(sourceType, effectiveAnno, new AnnotationMirror[]{target}, msgKey, node);
    }

    private void annoIsNoneOf(AnnotatedTypeMirror sourceType, AnnotationMirror effectiveAnno,
                              AnnotationMirror[] targets, String msgKey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el = slotManager.getSlot(effectiveAnno);

            if (el == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::isNoneOf: no annotation in type: " + targets);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::mainIsNoneOf: Inequality constraint constructor invocation(s).");

                    for (AnnotationMirror mod : targets) {
                        InferenceMain.getInstance().getConstraintManager()
                                .addInequalityConstraint(el, slotManager.getSlot(mod));
                    }
                }
            }
        } else {
            for (AnnotationMirror target : targets) {
                if (AnnotationUtils.areSame(target, effectiveAnno)) {
                    checker.reportError(node, msgKey, effectiveAnno, sourceType.toString(), node.toString());
                }
            }
        }
    }


    public void areComparable(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el1 = slotManager.getVariableSlot(ty1);
            Slot el2 = slotManager.getVariableSlot(ty2);

            if (el1 == null || el2 == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::areComparable: no annotation on type: " + ty1 + " or " + ty2);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::areComparable: Comparable constraint constructor invocation.");
                    InferenceMain.getInstance().getConstraintManager().addComparableConstraint(el1, el2);
                }
            }
        } else {
            if (!(atypeFactory.getTypeHierarchy().isSubtype(ty1, ty2) || atypeFactory.getTypeHierarchy().isSubtype(ty2, ty1))) {
                checker.reportError(node, msgkey, ty1.toString(), ty2.toString(), node.toString());
            }
        }
    }

    public void areEqual(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            Slot el1 = slotManager.getVariableSlot(ty1);
            Slot el2 = slotManager.getVariableSlot(ty2);

            if (el1 == null || el2 == null) {
                // TODO: prims not annotated in UTS, others might
                logger.warning("InferenceVisitor::areEqual: no annotation on type: " + ty1 + " or " + ty2);
            } else {
                if (!InferenceMain.getInstance().isPerformingFlow()) {
                    logger.fine("InferenceVisitor::areEqual: Equality constraint constructor invocation.");
                    InferenceMain.getInstance().getConstraintManager().addEqualityConstraint(el1, el2);
                }
            }
        } else {
            if (!ty1.equals(ty2)) {
                checker.reportError(node, msgkey, ty1.toString(), ty2.toString(), node.toString());
            }
        }
    }

    @Override
    protected void checkTypeArguments(Tree toptree,
                                      List<? extends AnnotatedTypeParameterBounds> paramBounds,
                                      List<? extends AnnotatedTypeMirror> typeargs,
                                      List<? extends Tree> typeargTrees) {
        // System.out.printf("BaseTypeVisitor.checkTypeArguments: %s, TVs: %s, TAs: %s, TATs: %s\n",
        //         toptree, paramBounds, typeargs, typeargTrees);

        // If there are no type variables, do nothing.
        if (paramBounds.isEmpty())
            return;

        assert paramBounds.size() == typeargs.size() :
                "BaseTypeVisitor.checkTypeArguments: mismatch between type arguments: " +
                        typeargs + " and type parameter bounds" + paramBounds;

        Iterator<? extends AnnotatedTypeParameterBounds> boundsIter = paramBounds.iterator();
        Iterator<? extends AnnotatedTypeMirror> argIter = typeargs.iterator();

        while (boundsIter.hasNext()) {

            AnnotatedTypeParameterBounds bounds = boundsIter.next();
            AnnotatedTypeMirror typeArg = argIter.next();

            AnnotatedTypeMirror varUpperBound = bounds.getUpperBound();
            final AnnotatedTypeMirror typeArgForUpperBoundCheck = typeArg;

            if (typeArg.getKind() == TypeKind.WILDCARD ) {

                if (bounds.getUpperBound().getKind() == TypeKind.WILDCARD) {
                    // TODO: When capture conversion is implemented, this special case should be removed.
                    // TODO: This may not occur only in places where capture conversion occurs but in those cases
                    // TODO: The containment check provided by this method should be enough
                    continue;
                }

                // If we have a declaration:
                // class MyClass<T extends String> ...
                //
                // the javac compiler allows wildcard type arguments that have Java types OUTSIDE of the
                // bounds of T, i.e:
                // MyClass<? extends Object>
                //
                // This is sound because every NON-WILDCARD reference to MyClass MUST obey those bounds
                // This leads to cases where varUpperBound is actually a subtype of typeArgForUpperBoundCheck
                final TypeMirror varUnderlyingUb = varUpperBound.getUnderlyingType();
                final TypeMirror argUnderlyingUb = ((AnnotatedWildcardType)typeArg).getExtendsBound().getUnderlyingType();
                if ( !types.isSubtype(argUnderlyingUb, varUnderlyingUb)
                        &&  types.isSubtype(varUnderlyingUb, argUnderlyingUb)) {
                    varUpperBound = AnnotatedTypes.asSuper(atypeFactory,
                            varUpperBound, typeArgForUpperBoundCheck);
                }
            }

            if (typeargTrees == null || typeargTrees.isEmpty()) {
                // The type arguments were inferred and we mark the whole method.
                // The inference fails if we provide invalid arguments,
                // therefore issue an error for the arguments.
                // I hope this is less confusing for users.
                commonAssignmentCheck(varUpperBound,
                        typeArg, toptree,
                        "type.argument.type.incompatible");
            } else {
                commonAssignmentCheck(varUpperBound, typeArg,
                        typeargTrees.get(typeargs.indexOf(typeArg)),
                        "type.argument.type.incompatible");
            }

            if (!atypeFactory.getTypeHierarchy().isSubtype(bounds.getLowerBound(), typeArg)) {
                if (typeargTrees == null || typeargTrees.isEmpty()) {
                    // The type arguments were inferred and we mark the whole method.
                    checker.reportError(toptree, "type.argument.type.incompatible",
                                    typeArg, bounds);
                } else {
                    checker.reportError(typeargTrees.get(typeargs.indexOf(typeArg)), "type.argument.type.incompatible",
                                    typeArg, bounds);
                }
            }
        }
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
            @CompilerMessageKey String errorKey) {
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
            var = atypeFactory.getAnnotatedTypeLhs(varTree);
        }

        assert var != null : "no variable found for tree: " + varTree;

        commonAssignmentCheck(var, valueExp, errorKey);
    }

    @Override
    protected void commonAssignmentCheck(AnnotatedTypeMirror varType,
            AnnotatedTypeMirror valueType, Tree valueTree, @CompilerMessageKey
            String errorKey) {
        // ####### Copied Code ########

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
        // ####### End Copied Code ########

        // Handle refinement variables.
        // If this is the result of an assignment,
        // instead of a subtype relationship we know the refinement variable
        // on the LHS must be equal to the variable on the RHS.
        if (infer) {
            maybeAddRefinementVariableConstraints(varType, valueType);
        }

        // this will also add a subtyping constraint between any refinement variables added and
        // the RHS of this comparison.  Those variables will already have an equality constraint
        // from the above maybeAddRefinementVariableConstraints this will at most bias solvers
        // towards breaking these constraints fewer times when solving
        // We keep the subtype check anyway for the sake of component types that should be compared
        // using this method
        // TODO: We should get rid of this if, but for now type variables will have their bounds
        // TODO: incorrectly inferred if we do not have it
        boolean success = true;
        if (!infer || (varType.getKind() != TypeKind.TYPEVAR && valueType.getKind() != TypeKind.TYPEVAR)) {
            success = atypeFactory.getTypeHierarchy().isSubtype(valueType, varType);
        }

        // ####### Copied Code ########
        // TODO: integrate with subtype test.
        if (success) {
            for (Class<? extends Annotation> mono : atypeFactory.getSupportedMonotonicTypeQualifiers()) {
                if (valueType.hasAnnotation(mono)
                        && varType.hasAnnotation(mono)) {
                    checker.reportError(valueTree, "monotonic.type.incompatible",
                                    mono.getCanonicalName(),
                                    mono.getCanonicalName(),
                                    valueType.toString());
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
            checker.reportError(valueTree, errorKey,
                    valueTypeString, varTypeString);
        }
        // ####### End Copied Code ########
    }

    private void addRefinementVariableConstraints(final AnnotatedTypeMirror varType,
                                                  final AnnotatedTypeMirror valueType,
                                                  final SlotManager slotManager,
                                                  final ConstraintManager constraintManager) {
        Slot sup = slotManager.getVariableSlot(varType);
        Slot sub = slotManager.getVariableSlot(valueType);
        logger.fine("InferenceVisitor::commonAssignmentCheck: Equality constraint for qualifiers sub: " + sub + " sup: " + sup);

        // Equality between the refvar and the value
        constraintManager.addEqualityConstraint(sup, sub);

        // Refinement variable still needs to be a subtype of its declared type value
        constraintManager.addSubtypeConstraint(sup, ((RefinementVariableSlot) sup).getRefined());
    }

    /**
     * A refinement variable generally has two constraints that must be enforce.  It must be a subtype of the
     * declared type it refines and it must be equal to the type on the right-hand side of the assignment or
     * pseudo-assignment that created it.
     *
     * This method detects the assignments that cause refinements and generates the above constraints.
     */
    public boolean maybeAddRefinementVariableConstraints(final AnnotatedTypeMirror varType, final AnnotatedTypeMirror valueType) {
        boolean inferenceRefinementVariable = false;
        final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
        final ConstraintManager constraintManager = InferenceMain.getInstance().getConstraintManager();

        // type variables have two refinement variables (one on the upper bound and one on the lower bound)
        if (varType.getKind() == TypeKind.TYPEVAR) {
            if (valueType.getKind() == TypeKind.TYPEVAR) {
                final AnnotatedTypeVariable varTypeTv = (AnnotatedTypeVariable) varType;

                final AnnotatedTypeMirror varUpperBoundAtm;
                final AnnotatedTypeMirror varLowerBoundAtm;

                try {
                    varUpperBoundAtm = InferenceUtil.findUpperBoundType(varTypeTv);
                    varLowerBoundAtm = InferenceUtil.findLowerBoundType(varTypeTv);

                } catch(Throwable exc) {
                    if (InferenceMain.isHackMode()) {
                        return false;
                    } else {
                        throw exc;
                    }
                }

                final Slot upperBoundSlot = slotManager.getVariableSlot(varUpperBoundAtm);
                final Slot lowerBoundSlot = slotManager.getVariableSlot(varLowerBoundAtm);
                if (upperBoundSlot instanceof RefinementVariableSlot
                        && lowerBoundSlot instanceof RefinementVariableSlot) {
                    final AnnotatedTypeVariable valueTypeTv = (AnnotatedTypeVariable) valueType;
                    final AnnotatedTypeMirror valUpperBoundAtm;
                    final AnnotatedTypeMirror valLowerBoundAtm;
                    try {
                        valUpperBoundAtm = InferenceUtil.findUpperBoundType(valueTypeTv);
                        valLowerBoundAtm = InferenceUtil.findLowerBoundType(valueTypeTv);
                    } catch(Throwable exc) {
                        if (InferenceMain.isHackMode()) {
                            return false;
                        } else {
                            throw exc;
                        }
                    }
                    addRefinementVariableConstraints(varUpperBoundAtm, valUpperBoundAtm, slotManager, constraintManager);

                    constraintManager.addEqualityConstraint(lowerBoundSlot,
                            slotManager.getVariableSlot(valLowerBoundAtm));
                    constraintManager.addSubtypeConstraint(lowerBoundSlot, upperBoundSlot);

                    inferenceRefinementVariable = true;
                }

            } else if (valueType.getKind() == TypeKind.NULL) {
                // TODO: For now do nothing but we should be doing some refinement

            } else {
                if (!InferenceMain.isHackMode()) {
                    throw new BugInCF("Unexpected assignment to type variable"); // TODO: Either more detail, or remove because of type args?
                    // TODO: OR A DIFFERENT SET OF CONSTRAINTS?
                }
            }
        } else {

            // TODO: RECONSIDER THIS WHEN WE CONSIDER WILDCARDS
            if (varType.getKind() != TypeKind.WILDCARD) {
                Slot sup = InferenceMain.getInstance().getSlotManager().getVariableSlot(varType);
                if (sup instanceof RefinementVariableSlot && !InferenceMain.getInstance().isPerformingFlow()) {
                    inferenceRefinementVariable = true;

                    final AnnotatedTypeMirror upperBound;
                    if (valueType.getKind() == TypeKind.TYPEVAR) {
                        upperBound = InferenceUtil.findUpperBoundType((AnnotatedTypeVariable) valueType);
                    } else {
                        upperBound = valueType;
                    }

                    Slot sub = slotManager.getVariableSlot(upperBound);
                    logger.fine("InferenceVisitor::commonAssignmentCheck: Equality constraint for qualifiers sub: " + sub + " sup: " + sup);

                    // Equality between the refvar and the value
                    constraintManager.addEqualityConstraint(sup, sub);

                    // Refinement variable still needs to be a subtype of its declared type value
                    constraintManager.addSubtypeConstraint(sup,
                            ((RefinementVariableSlot) sup).getRefined());
                }
            }
        }

        return inferenceRefinementVariable;
    }

    protected Set<AnnotationMirror> filterThrowCatchBounds(Set<? extends AnnotationMirror> originals) {
        Set<AnnotationMirror> throwBounds = new HashSet<>();

        for (AnnotationMirror throwBound : originals) {
            if (AnnotationUtils.areSameByClass(throwBound, VarAnnot.class)) {
                if (throwBound.getElementValues().size() != 0) {
                    throwBounds.add(throwBound);
                }
            } else if (!AnnotationUtils.areSameByClass(throwBound, Unqualified.class)) {
                // throwBound represents the qualifier which all thrown types must be subtypes of
                // there is not point in enforcing thrownType <: TOP, since it will always be true
                AnnotationMirror top = atypeFactory.getQualifierHierarchy().getTopAnnotation(throwBound);
                if (!AnnotationUtils.areSame(top, throwBound)) {
                    throwBounds.add(throwBound);
                }
            }
        }
        return throwBounds;
    }

    @Override
    protected void checkThrownExpression(ThrowTree node) {
        if (infer) {
            // TODO: We probably want to unify this code with BaseTypeVisitor
            AnnotatedTypeMirror throwType = atypeFactory.getAnnotatedType(node
                    .getExpression());
            Set<AnnotationMirror> throwBounds = filterThrowCatchBounds(getThrowUpperBoundAnnotations());

            final AnnotationMirror varAnnot =new AnnotationBuilder(atypeFactory.getProcessingEnv(), VarAnnot.class).build();
            final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
            final ConstraintManager constraintManager = InferenceMain.getInstance().getConstraintManager();
            for (AnnotationMirror throwBound : throwBounds) {
                switch (throwType.getKind()) {
                    case NULL:
                    case DECLARED:
                    constraintManager.addSubtypeConstraint(slotManager.getVariableSlot(throwType),
                            slotManager.getSlot(throwBound)
                        );
                        break;
                    case TYPEVAR:
                    case WILDCARD:
                        AnnotationMirror foundEffective = AnnotatedTypes.findEffectiveAnnotationInHierarchy(
                            atypeFactory.getQualifierHierarchy(), throwType, varAnnot);
                    constraintManager.addSubtypeConstraint(slotManager.getSlot(foundEffective),
                            slotManager.getSlot(throwBound)
                        );
                        break;

                    case UNION:
                        AnnotatedUnionType unionType = (AnnotatedUnionType) throwType;
                        AnnotationMirror primary = unionType.getAnnotationInHierarchy(varAnnot);
                        if (primary != null) {
                        constraintManager.addSubtypeConstraint(slotManager.getSlot(primary),
                                slotManager.getSlot(throwBound)
                            );
                        }

                        for (AnnotatedTypeMirror altern : unionType.getAlternatives()) {
                            AnnotationMirror alternAnno = altern.getAnnotationInHierarchy(varAnnot);
                            if (alternAnno != null) {
                            constraintManager.addSubtypeConstraint(slotManager.getSlot(alternAnno),
                                    slotManager.getSlot(throwBound)
                                );
                            }
                        }
                        break;

                    default:
                        throw new BugInCF("Unexpected throw expression type: "
                                + throwType.getKind());
                }
            }


        }  else {
            super.checkThrownExpression(node);
        }

    }

    // TODO: TEMPORARY HACK UNTIL WE SUPPORT UNIONS
    private boolean isUnion(Tree tree) {
        if (tree.getKind() == Kind.VARIABLE) {
            return ((VariableTree) tree).getType().getKind() == Kind.UNION_TYPE;
        }

        return tree.getKind() == Kind.UNION_TYPE;
    }

    @Override
    protected void checkExceptionParameter(CatchTree node) {

        if (infer) {
            // TODO: Unify with BaseTypeVisitor implementation
            Set<AnnotationMirror> requiredAnnotations = filterThrowCatchBounds(getExceptionParameterLowerBoundAnnotations());
            AnnotatedTypeMirror exPar = atypeFactory.getAnnotatedType(node.getParameter());

            for (AnnotationMirror required : requiredAnnotations) {
                AnnotationMirror found = exPar.getAnnotationInHierarchy(required);
                assert found != null;

                if (exPar.getKind() != TypeKind.UNION) {
                    if (!atypeFactory.getQualifierHierarchy()
                            .isSubtype(required, found)) {
                        checker.reportError(node.getParameter(), "exception.parameter.invalid",
                                found, required);
                    }
                } else {
                    AnnotatedUnionType aut = (AnnotatedUnionType) exPar;
                    for (AnnotatedTypeMirror alterntive : aut.getAlternatives()) {
                        AnnotationMirror foundAltern = alterntive
                                .getAnnotationInHierarchy(required);
                        if (!atypeFactory.getQualifierHierarchy().isSubtype(
                                required, foundAltern)) {
                            checker.reportError(node.getParameter(), "exception.parameter.invalid", foundAltern,
                                    required);
                        }
                        }
                }
            }

        } else {
            super.checkExceptionParameter(node);
        }
    }

    // TODO: WE NEED TO FIX this method and have it do something sensible
    // TODO: The issue here is that I have removed the error reporting from this method
    // TODO: In order to allow verigames to move forward.
    /**
     * Tests whether the tree expressed by the passed type tree is a valid type,
     * and emits an error if that is not the case (e.g. '@Mutable String').
     * If the tree is a method or constructor, check the return type.
     *
     * @param tree  the AST type supplied by the user
     */
    @Override
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

        // TODO: THIS MIGHT FAIL
//        typeValidator.isValid(type, tree);
        // more checks (also specific to checker, potentially)
        return true;
    }

    @Override
    protected InferenceValidator createTypeValidator() {
        return new InferenceValidator(checker, this, atypeFactory);
    }
    
    @Override
    // Do NOT perform this check until issue #218 is resolved
    protected void checkConstructorResult(
            AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {}
    
}
