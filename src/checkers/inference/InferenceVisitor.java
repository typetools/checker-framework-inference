package checkers.inference;

import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;

import checkers.basetype.BaseTypeChecker;
import checkers.basetype.BaseTypeVisitor;
import checkers.inference.quals.VarAnnot;
import checkers.source.Result;
import checkers.types.*;
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable;
import checkers.util.AnnotatedTypes;
import javacutils.InternalUtils;
import javacutils.TreeUtils;
import javacutils.TypesUtils;

import com.sun.source.tree.*;
import scala.Option;

import static checkers.inference.InferenceMain.slotMgr;
import static checkers.inference.InferenceMain.constraintMgr;

public class InferenceVisitor extends BaseTypeVisitor<BaseTypeChecker<InferenceAnnotatedTypeFactory<?>>, InferenceAnnotatedTypeFactory<?>> {

    /* One design alternative would have been to use two separate subclasses instead of the boolean.
     * However, this separates the inference and checking implementation of a method.
     * Using the boolean, the two implementations are closer together.
     *
     */
    protected final boolean infer;

    public InferenceVisitor(BaseTypeChecker checker, CompilationUnitTree root, boolean infer) {
        super(checker, root);

        this.infer = infer;
        ((InferenceChecker) checker).methodInvocationToTypeArgs().clear();
    }

    public InferenceVisitor(BaseTypeChecker checker, CompilationUnitTree root, InferenceChecker ichecker, boolean infer) {
        this(checker, root, infer);
    }


    @Override
    public boolean isValidUse(final AnnotatedDeclaredType declarationType,
                              final AnnotatedDeclaredType useType) {
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

        Slot el = slotMgr().extractSlot(ty);

        if (el == null) {
            if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                // TODO: prims not annotated in UTS, others might
                System.out.println("InferenceVisitor::doesNotContain: no annotation in type: " + ty);
            }
        } else {
            if(! InferenceMain.isPerformingFlow() ) {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::doesNotContain: Inequality constraint constructor invocation(s).");
                }

                VariablePosition contextvp = ConstraintManager.constructConstraintPosition(atypeFactory, node);

                for (AnnotationMirror mod : mods) {
                    // TODO: are Constants compared correctly???
                    constraintMgr().addInequalityConstraint(contextvp, el, new Constant(mod));
                }
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
            Slot el = slotMgr().extractSlot(ty);

            if (el == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::mainIs: no annotation in type: " + ty);
                }
            } else {
                if(!InferenceMain.isPerformingFlow()) {
                    if (InferenceMain.DEBUG(this)) {
                        System.out.println("InferenceVisitor::mainIs: Equality constraint constructor invocation(s).");
                    }


                    constraintMgr().addEqualityConstraint(el, new Constant(mod));
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
            //String el1 = slotMgr().extractSlot(ty).toString() + " "; //TODO JB: Remove - temporarily here for Debugging
            Slot el = slotMgr().extractSlot(ty);

            if (el == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::isNoneOf: no annotation in type: " + ty);
                }
            } else {
                if( !InferenceMain.isPerformingFlow() ) {
                    if (InferenceMain.DEBUG(this)) {
                        System.out.println("InferenceVisitor::mainIsNoneOf: Inequality constraint constructor invocation(s).");
                    }

                    VariablePosition contextvp = ConstraintManager.constructConstraintPosition(atypeFactory, node);

                    for (AnnotationMirror mod : mods) {
                        constraintMgr().addInequalityConstraint(contextvp, el, new Constant(mod));
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


    //TODO: WE NEED TO FIX this method and have it do something sensible
    //TODO: The issue here is that I have removed the error reporting from this method
    //TODO: In order to allow verigames to move forward.
    /**
     * Tests whether the tree expressed by the passed type tree is a valid type,
     * and emits an error if that is not the case (e.g. '@Mutable String').
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
            default:
                type = atypeFactory.getAnnotatedType(tree);
        }

        AnnotatedTypes.isValidType(checker.getQualifierHierarchy(), type);
        return true;
    }

    public void areComparable(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            Slot el1 = slotMgr().extractSlot(ty1);
            Slot el2 = slotMgr().extractSlot(ty2);

            if (el1 == null || el2 == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty1) ||
                        InferenceMain.getRealChecker().needsAnnotation(ty2)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::areComparable: no annotation on type: " + ty1 + " or " + ty2);
                }
            } else {
                if( !InferenceMain.isPerformingFlow() ) {
                    if (InferenceMain.DEBUG(this)) {
                        System.out.println("InferenceVisitor::areComparable: Comparable constraint constructor invocation.");
                    }

                    constraintMgr().addComparableConstraint(el1, el2);
                }
            }
        } else {
            if (!(checker.getTypeHierarchy().isSubtype(ty1, ty2) || checker.getTypeHierarchy().isSubtype(ty2, ty1))) {
                checker.report(Result.failure(msgkey, ty1.toString(), ty2.toString(), node.toString()), node);
            }
        }
    }

    public void areEqual(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            Slot el1 = slotMgr().extractSlot(ty1);
            Slot el2 = slotMgr().extractSlot(ty2);

            if (el1 == null || el2 == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty1) ||
                        InferenceMain.getRealChecker().needsAnnotation(ty2)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::areEqual: no annotation on type: " + ty1 + " or " + ty2);
                }
            } else {
                if( !InferenceMain.isPerformingFlow() ) {
                    if (InferenceMain.DEBUG(this)) {
                        System.out.println("InferenceVisitor::areEqual: Equality constraint constructor invocation.");
                    }

                    constraintMgr().addEqualityConstraint(el1, el2);
                }
            }
        } else {
            if (!ty1.equals(ty2)) {
                checker.report(Result.failure(msgkey, ty1.toString(), ty2.toString(), node.toString()), node);
            }
        }
    }


    /* I'm not happy that I have to override this method. But at one point the
     * annotation sets are compared for equality and that doesn't work for inference.
     * Is there a nicer solution, as everything else is the same?
     * 
     * @see checkers.basetype.BaseTypeVisitor#checkTypeArguments(com.sun.source.tree.Tree, java.util.List, java.util.List, java.util.List)
     */
    @Override
    protected void checkTypeArguments(Tree toptree,
            List<? extends AnnotatedTypeVariable> typevars,
            List<? extends AnnotatedTypeMirror> typeargs,
            List<? extends Tree> typeargTrees) {

        if (!infer) {
            super.checkTypeArguments(toptree, typevars, typeargs, typeargTrees);
            return;
        }

        if (typevars.isEmpty()) return;
        assert typevars.size() == typeargs.size() :
            "InferenceVisitor.checkTypeArguments: mismatch between type arguments: " +
            typeargs + " and type variables" + typevars;

        Iterator<? extends AnnotatedTypeVariable> varIter = typevars.iterator();
        Iterator<? extends AnnotatedTypeMirror> argIter = typeargs.iterator();

        while (varIter.hasNext()) {

            AnnotatedTypeVariable typeVar = varIter.next();
            AnnotatedTypeMirror typearg = argIter.next();
            TypeParameterElement typeParameterElement = (TypeParameterElement) typeVar.getUnderlyingType().asElement();

            final Option<AnnotatedTypeVariable> kludgeLower =
                    ((InferenceChecker) checker).typeParamElemCache().get(typeParameterElement);

            //TODO JB: Major Kludge for lack of access to upper bound annotation on typeVariables
            //TODO JB: due to the fact that it gets overwritten by the primary annotation
            final Option<AnnotatedTypeVariable> kludgeUpper =
                    ((InferenceChecker) checker).typeParamElemToUpperBound().get(typeParameterElement);

            if (typearg.getKind() == TypeKind.WILDCARD) continue;

            //TODO JB: Figure out where/why this can be empty and fix
            if( kludgeUpper.isDefined() ) {
                final AnnotatedTypeVariable declaredUpper = kludgeUpper.get();
                final AnnotatedTypeVariable declaredLower = kludgeLower.get();
                if (declaredUpper.getUpperBound() != null && kludgeUpper.isDefined())  {    //TODO JB: Figure out what's going on here?

                    if ( !TypesUtils.isObject(typeVar.getUpperBound().getUnderlyingType() )
                            || InferenceUtils.isAnnotated(declaredUpper.getUpperBound()) ) {
                        if (typeargTrees == null || typeargTrees.isEmpty()) {
                            commonAssignmentCheck(declaredUpper.getUpperBound(), typearg,
                                    toptree,
                                    "argument.type.incompatible", false);
                        } else {
                            commonAssignmentCheck(declaredUpper.getUpperBound(), typearg,
                                    typeargTrees.get(typeargs.indexOf(typearg)),
                                    "generic.argument.invalid", false);
                        }
                    }
                }

                AnnotatedTypeMirror taForUpper = typearg;
                if( typearg instanceof AnnotatedTypeVariable ) {
                    final AnnotatedTypeVariable typeArgTv = (AnnotatedTypeVariable) typearg;
                    final TypeParameterElement typeArgTpElem = (TypeParameterElement) typeArgTv.getUnderlyingType().asElement();
                    taForUpper = ((InferenceChecker) checker).typeParamElemToUpperBound().apply(typeArgTpElem);
                }

                InferenceAnnotationUtils.traverseAndSubtype(taForUpper, declaredUpper.getUpperBound());
                InferenceAnnotationUtils.traverseAndSubtype(declaredLower, typearg);

                if (!declaredUpper.getAnnotations().isEmpty() && !InferenceMain.isPerformingFlow()) {
                    // BaseTypeVisitor does
                    // if (!typearg.getAnnotations().equals(typeVar.getAnnotationsOnTypeVar())) {
                    // Instead, we go through all annotations and create equality constraints for them.

                    java.util.Set<AnnotationMirror> taannos = typearg.getAnnotations();
                    java.util.Set<AnnotationMirror> tvannos = typeVar.getAnnotations();

                    for (AnnotationMirror ta : taannos) {
                        constraintMgr().addSubtypeConstraint( ta, declaredUpper.getAnnotation(VarAnnot.class) );
                        constraintMgr().addSubtypeConstraint( declaredLower.getAnnotation(VarAnnot.class), ta );

                        for (AnnotationMirror tv : tvannos) {
                            if (InferenceMain.DEBUG(this)) {
                                System.out.println("InferenceVisitor::checkTypeArguments: Subtype constraint constructor invocation.");
                            }

                            constraintMgr().addSubtypeConstraint(tv, ta);
                        }
                    }
                }
            }
        }
    }

    /**
     * Log the invocation of a method.
     * Call this method from
     *
     *     public Void visitMethodInvocation(MethodInvocationTree node, Void p)
     *
     * I don't want to override that method, as not every type system needs this functionality. 
     */
    public void logMethodInvocation(MethodInvocationTree node) {
        if (infer && !InferenceMain.isPerformingFlow()) {
            if (InferenceMain.DEBUG(this)) {
                System.out.println("InferenceVisitor::logMethodInvocation: creating CallInstanceMethodConstraint.");
            }

            constraintMgr().addInstanceMethodCallConstraint(atypeFactory, trees, node);
        } else {
            // Nothing to do in checking mode. 
        }
    }

    /**
     * Log an assignment.
     * Call this method from
     *
     *     public Void visitAssignment(AssignmentTree node, Void p)
     *
     * I don't want to override that method, as not every type system needs this functionality. 
     */
    public void logAssignment(AssignmentTree node) {
        if (infer && !InferenceMain.isPerformingFlow()) {
            if (InferenceMain.DEBUG(this)) {
                System.out.println("InferenceVisitor::logAssignment: creating AssignmentConstraint.");
            }
            constraintMgr().addFieldAssignmentConstraint( atypeFactory, trees, node );
        } else {
            // Nothing to do in checking mode. 
        }
    }

    /**
     * Log the access to a field.
     * Call this method from
     *
     *     public Void visitMemberSelect(MemberSelectTree node, Void p)
     *
     * I don't want to override that method, as not every type system needs this functionality. 
     */
    public void logFieldAccess(ExpressionTree node) {
        if (infer && !InferenceMain.isPerformingFlow()) {
            Element elem = TreeUtils.elementFromUse(node);
            if (elem.getKind().isField()) {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::logFieldAccess: creating FieldAccessConstraint for node: " + node);
                }
                constraintMgr().addFieldAccessConstraint( atypeFactory, trees, node );
            }
        } else {
            // Nothing to do in checking mode. 
        }
    }

    // TODO JB: Why are all of the calls to log<some constraints> in GameVisitor yet defined here?
    // TODO JB: Seems pretty weird?
    /** TODO JB: Ask Mike/Werner, the subtype constraint between receiver parameter and receiver seems
     *  TODO JB: to be handled by by CallInstanceMethod Constraints
     * @param methodInvocationTree

    public void logReceiverInvocationConstraints( final MethodInvocationTree methodInvocationTree ) {
        methodInvocationTree.getMethodSelect();
    } */

    /**
     * Given a receiver parameter variable declaration, create a subtype constraint between it and the enclosing class'
     * extends clause
     * i.e.
     * @ReceiverParamAnno <: @ExtendsAnno
     *
     * @param methodTree A MethodTree for the receiver parameter we wish to annotate
     */

    public void logReceiverClassConstraints(final MethodTree methodTree) {

        if (infer && !InferenceMain.isPerformingFlow()) {
            final SlotManager slotManager = slotMgr();
            final ClassTree classTree     = TreeUtils.enclosingClass( getCurrentPath() );
            final ExecutableElement methodElem = TreeUtils.elementFromDeclaration(methodTree);

            final Option<AnnotatedExecutableType> methodTypeOpt = ((InferenceChecker) checker).exeElemCache().get( methodElem );
            final Option<Slot> extendsSlotOpt = slotManager.getPrimaryExtendsAnno( classTree );

            //TODO JB: Find out which methods this is true for and why?  This should not happen
            if( methodTypeOpt.isDefined() && extendsSlotOpt.isDefined() ) {
                final AnnotatedExecutableType methodType = methodTypeOpt.get();

                final Slot extendsSlot  = extendsSlotOpt.get();
                final Slot receiverSlot = slotManager.extractSlot( methodType.getReceiverType() );
                constraintMgr().addSubtypeConstraint( receiverSlot, extendsSlot );
            }
        }

    }

    public void logConstructorConstraints(final MethodTree methodTree) {

        if (infer && !InferenceMain.isPerformingFlow()) {
            final SlotManager slotManager = slotMgr();
            final ClassTree classTree = TreeUtils.enclosingClass( getCurrentPath() );
            ExecutableElement exeElem = TreeUtils.elementFromDeclaration( methodTree );

            //TODO JB: This shouldn't be optional, figure out why some of these are missing
            Option<AnnotatedExecutableType> constructorAtmOpt = ((InferenceChecker) checker).exeElemCache().get( exeElem );
            final Option<Slot> extendsSlotOpt = slotManager.getPrimaryExtendsAnno( classTree );

            if( constructorAtmOpt.isDefined() && extendsSlotOpt.isDefined() ) {
                AnnotatedExecutableType constructorAtm = constructorAtmOpt.get();

                final Slot extendsSlot  = extendsSlotOpt.get();
                final Slot constructorReturnSlot = slotManager.extractSlot( constructorAtm.getReturnType() );

                constraintMgr().addSubtypeConstraint( constructorReturnSlot , extendsSlot );
            }
        }
    }

    public void logConstructorInvocationConstraints(final NewClassTree newClassTree) {
        if (infer && !InferenceMain.isPerformingFlow()) {
            constraintMgr().addConstructorInvocationConstraint(atypeFactory, trees, newClassTree );

            final ExecutableElement constructorElem = InternalUtils.constructor( newClassTree );

            //TODO JB: This shouldn't be optional, figure out why some of these are missing
            /*Option<AnnotatedExecutableType> constructorAtmOpt = ((InferenceChecker) checker).exeElemCache().get( constructorElem );
            if( constructorAtmOpt.isDefined() ) {
                AnnotatedExecutableType constructorAtm = constructorAtmOpt.get();
                final Slot constructorSlot = slotMgr().extractSlot( constructorAtm.getReturnType() );
                final Slot newClassSlot    = slotMgr().extractSlot( atypeFactory.getAnnotatedType( newClassTree ) );

                constraintMgr().addSubtypeConstraint( newClassSlot, constructorSlot );
            } */
        }
    }

    public void logTypeParameterConstraints(final TypeParameterTree typeParameterTree ) {
        final AnnotatedTypeVariable atmTv = (AnnotatedTypeVariable) atypeFactory.getAnnotatedTypeFromTypeTree(typeParameterTree);
        final AnnotatedTypeVariable kludgeUpper =
                ((InferenceChecker) checker).typeParamElemToUpperBound()
                    .apply((TypeParameterElement) atmTv.getUnderlyingType().asElement());

        final Slot lowerBound = slotMgr().extractSlot( atmTv );
        final Slot upperBound    = slotMgr().extractSlot(kludgeUpper.getUpperBound());
        constraintMgr().addSubtypeConstraint( lowerBound, upperBound );
    }
}