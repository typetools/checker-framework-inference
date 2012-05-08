package checkers.inference;

import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;

import checkers.basetype.BaseTypeChecker;
import checkers.basetype.BaseTypeVisitor;
import checkers.source.Result;
import checkers.types.*;
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable;
import checkers.util.TreeUtils;
import checkers.util.TypesUtils;

import com.sun.source.tree.*;

public class InferenceVisitor extends BaseTypeVisitor<BaseTypeChecker> {

    /* One design alternative would have been to use two separate subclasses instead of the boolean.
     * However, this separates the inference and checking implementation of a method.
     * Using the boolean, the two implementations are closer together.
     *
     */
    protected final boolean infer;

    public InferenceVisitor(BaseTypeChecker checker, CompilationUnitTree root, boolean infer) {
        super(checker, root);

        this.infer = infer;
    }

    public InferenceVisitor(BaseTypeChecker checker, CompilationUnitTree root, InferenceChecker ichecker, boolean infer) {
        this(checker, root, infer);
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

        Slot el = InferenceMain.slotMgr().extractSlot(ty);

        if (el == null) {
            if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                // TODO: prims not annotated in UTS, others might
                System.out.println("InferenceVisitor::doesNotContain: no annotation in type: " + ty);
            }
        } else {
            if (InferenceMain.DEBUG(this)) {
                System.out.println("InferenceVisitor::doesNotContain: Inequality constraint constructor invocation(s).");
            }

            VariablePosition contextvp = ConstraintManager.constructConstraintPosition((InferenceAnnotatedTypeFactory) atypeFactory, node);

            for (AnnotationMirror mod : mods) {
                // TODO: are Constants compared correctly???
                InferenceMain.constraintMgr().addInequalityConstraint(contextvp, el, new Constant(mod));
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
            Slot el = InferenceMain.slotMgr().extractSlot(ty);

            if (el == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::mainIs: no annotation in type: " + ty);
                }
            } else {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::mainIs: Equality constraint constructor invocation(s).");
                }

                InferenceMain.constraintMgr().addEqualityConstraint(el, new Constant(mod));
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
            Slot el = InferenceMain.slotMgr().extractSlot(ty);

            if (el == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::isNoneOf: no annotation in type: " + ty);
                }
            } else {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::mainIsNoneOf: Inequality constraint constructor invocation(s).");
                }

                VariablePosition contextvp = ConstraintManager.constructConstraintPosition((InferenceAnnotatedTypeFactory) atypeFactory, node);

                for (AnnotationMirror mod : mods) {
                    InferenceMain.constraintMgr().addInequalityConstraint(contextvp, el, new Constant(mod));
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
            Slot el1 = InferenceMain.slotMgr().extractSlot(ty1);
            Slot el2 = InferenceMain.slotMgr().extractSlot(ty2);

            if (el1 == null || el2 == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty1) ||
                        InferenceMain.getRealChecker().needsAnnotation(ty2)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::areComparable: no annotation on type: " + ty1 + " or " + ty2);
                }
            } else {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::areComparable: Comparable constraint constructor invocation.");
                }

                InferenceMain.constraintMgr().addComparableConstraint(el1, el2);
            }
        } else {
            if (!(checker.isSubtype(ty1, ty2) || checker.isSubtype(ty2, ty1))) {
                checker.report(Result.failure(msgkey, ty1.toString(), ty2.toString(), node.toString()), node);
            }
        }
    }

    public void areEqual(AnnotatedTypeMirror ty1, AnnotatedTypeMirror ty2, String msgkey, Tree node) {
        if (infer) {
            Slot el1 = InferenceMain.slotMgr().extractSlot(ty1);
            Slot el2 = InferenceMain.slotMgr().extractSlot(ty2);

            if (el1 == null || el2 == null) {
                if (InferenceMain.getRealChecker().needsAnnotation(ty1) ||
                        InferenceMain.getRealChecker().needsAnnotation(ty2)) {
                    // TODO: prims not annotated in UTS, others might
                    System.out.println("InferenceVisitor::areEqual: no annotation on type: " + ty1 + " or " + ty2);
                }
            } else {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::areEqual: Equality constraint constructor invocation.");
                }

                InferenceMain.constraintMgr().addEqualityConstraint(el1, el2);
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

            if (typearg.getKind() == TypeKind.WILDCARD) continue;

            if (typeVar.getUpperBound() != null)  {
                if (!(TypesUtils.isObject(typeVar.getUpperBound().getUnderlyingType())
                        && !typeVar.getUpperBound().isAnnotated())) {
                    if (typeargTrees == null || typeargTrees.isEmpty()) {
                        commonAssignmentCheck(typeVar.getUpperBound(), typearg,
                                toptree,
                                "argument.type.incompatible");
                    } else {
                        commonAssignmentCheck(typeVar.getUpperBound(), typearg,
                                typeargTrees.get(typeargs.indexOf(typearg)),
                                "generic.argument.invalid");
                    }
                }
            }

            if (!typeVar.getAnnotations().isEmpty()) {
                // BaseTypeVisitor does
                // if (!typearg.getAnnotations().equals(typeVar.getAnnotationsOnTypeVar())) {
                // Instead, we go through all annotations and create equality constraints for them.

                java.util.Set<AnnotationMirror> taannos = typearg.getAnnotations();
                java.util.Set<AnnotationMirror> tvannos = typeVar.getAnnotations();

                for (AnnotationMirror ta : taannos) {
                    for (AnnotationMirror tv : tvannos) {
                        if (InferenceMain.DEBUG(this)) {
                            System.out.println("InferenceVisitor::checkTypeArguments: Equality constraint constructor invocation.");
                        }

                        InferenceMain.constraintMgr().addEqualityConstraint(ta, tv);
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
        if (infer) {
            if (InferenceMain.DEBUG(this)) {
                System.out.println("InferenceVisitor::logMethodInvocation: creating CallInstanceMethodConstraint.");
            }
            InferenceMain.constraintMgr().addCallInstanceMethodConstraint((InferenceAnnotatedTypeFactory) atypeFactory,
                    trees, node);
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
        if (infer) {
            if (InferenceMain.DEBUG(this)) {
                System.out.println("InferenceVisitor::logAssignment: creating AssignmentConstraint.");
            }
            InferenceMain.constraintMgr().addAssignmentConstraint((InferenceAnnotatedTypeFactory) atypeFactory,
                    trees, node);
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
        if (infer) {
            Element elem = TreeUtils.elementFromUse(node);
            if (elem.getKind().isField()) {
                if (InferenceMain.DEBUG(this)) {
                    System.out.println("InferenceVisitor::logFieldAccess: creating FieldAccessConstraint for node: " + node);
                }
                InferenceMain.constraintMgr().addFieldAccessConstraint((InferenceAnnotatedTypeFactory) atypeFactory,
                        trees, node);
            }
        } else {
            // Nothing to do in checking mode. 
        }
    }
}