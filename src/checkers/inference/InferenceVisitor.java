package checkers.inference;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.logging.Logger;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.BooleanLiteralNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.util.PurityChecker;
import org.checkerframework.dataflow.util.PurityChecker.PurityResult;
import org.checkerframework.dataflow.util.PurityUtils;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFAbstractValue;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.Unused;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.source.SourceVisitor;
import org.checkerframework.framework.type.*;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.framework.util.ContractsUtils;
import org.checkerframework.framework.util.FlowExpressionParseUtil;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionContext;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionParseException;
import org.checkerframework.framework.util.PluginUtil;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import checkers.inference.InferenceChecker;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;

public class InferenceVisitor<Checker extends BaseTypeChecker,
                              Factory extends GenericAnnotatedTypeFactory<?, ?, ?, ?>>
        extends SourceVisitor<Void, Void> {

    private static final Logger logger = Logger.getLogger(InferenceVisitor.class.getName());

    /* One design alternative would have been to use two separate subclasses instead of the boolean.
     * However, this separates the inference and checking implementation of a method.
     * Using the boolean, the two implementations are closer together.
     *
     */
    protected final boolean infer;

    protected final Checker realChecker;

    /** The {@link BaseTypeChecker} for error reporting. */
    protected final BaseTypeChecker checker;

    /** The factory to use for obtaining "parsed" version of annotations. */
    protected final Factory atypeFactory;

    /** For obtaining line numbers in -Ashowchecks debugging output. */
    protected final SourcePositions positions;

    /** For storing visitor state. **/
    protected final VisitorState visitorState;

    /** An instance of the {@link ContractsUtils} helper class. */
    protected final ContractsUtils contractsUtils;

    /**
     * @param checker
     *            the type-checker associated with this visitor (for callbacks to
     *            {@link TypeHierarchy#isSubtype})
     */
    public InferenceVisitor(Checker checker, InferenceChecker ichecker, Factory factory, boolean infer) {
        super(checker);
        this.realChecker = checker;
        this.checker = (infer) ? ichecker : checker;
        this.infer = infer;
        this.atypeFactory = factory;

        this.contractsUtils = ContractsUtils.getInstance(atypeFactory);
        this.positions = trees.getSourcePositions();
        this.visitorState = atypeFactory.getVisitorState();
        this.typeValidator = createTypeValidator();
        this.vectorType = atypeFactory.fromElement(elements.getTypeElement("java.util.Vector"));
    }

    public InferrableChecker realChecker() {
        return (InferrableChecker) realChecker;
    }

    public InferenceChecker getInferenceChecker() {
        return (InferenceChecker) checker;
    }

    public SlotManager getSlotManager() {
        return InferenceMain.getInstance().getSlotManager();
    }

    public ConstraintManager getConstraintManager() {
        return InferenceMain.getInstance().getConstraintManager();
    }

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
                getConstraintManager().add(new InequalityConstraint(el, new ConstantSlot(mod)));
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
                    getConstraintManager().add(new EqualityConstraint(el, new ConstantSlot(mod)));
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
                    getConstraintManager().add(new SubtypeConstraint(el, new ConstantSlot(mod)));
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
                        getConstraintManager().add(new InequalityConstraint(el, new ConstantSlot(mod)));
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
                    getConstraintManager().add(new ComparableConstraint(el1, el2));
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
                    getConstraintManager().add(new EqualityConstraint(el1, el2));
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
    public void checkTypeArguments(Tree toptree,
            List<? extends AnnotatedTypeParameterBounds> typevars,
            List<? extends AnnotatedTypeMirror> typeargs,
            List<? extends Tree> typeargTrees) {

//        if (!infer) {
//            checkTypeArgumentsCheck(toptree, typevars, typeargs, typeargTrees);
//            return;
//        }
//
//        if (typevars.isEmpty()) return;
//        assert typevars.size() == typeargs.size() :
//            "InferenceVisitor.checkTypeArguments: mismatch between type arguments: " +
//            typeargs + " and type variables" + typevars;
//
//        Iterator<? extends AnnotatedTypeVariable> varIter = typevars.iterator();
//        Iterator<? extends AnnotatedTypeMirror> argIter = typeargs.iterator();
//
//        while (varIter.hasNext()) {
//
//            AnnotatedTypeVariable typeVar = varIter.next();
//            AnnotatedTypeMirror typearg = argIter.next();
//            TypeParameterElement typeParameterElement = (TypeParameterElement) typeVar.getUnderlyingType().asElement();
//
//            final Option<AnnotatedTypeVariable> kludgeLower =
//                    ((InferenceChecker) checker).typeParamElemCache().get(typeParameterElement);
//
//            //TODO JB: Major Kludge for lack of access to upper bound annotation on typeVariables
//            //TODO JB: due to the fact that it gets overwritten by the primary annotation
//            final Option<AnnotatedTypeVariable> kludgeUpper =
//                    ((InferenceChecker) checker).typeParamElemToUpperBound().get(typeParameterElement);
//
//            if (typearg.getKind() == TypeKind.WILDCARD) continue;
//
//            //TODO JB: Figure out where/why this can be empty and fix
//            if( kludgeUpper.isDefined() ) {
//                final AnnotatedTypeVariable declaredUpper = kludgeUpper.get();
//                final AnnotatedTypeVariable declaredLower = kludgeLower.get();
//                if (declaredUpper.getUpperBound() != null && kludgeUpper.isDefined())  {    //TODO JB: Figure out what's going on here?
//
//                    if ( !TypesUtils.isObject(typeVar.getUpperBound().getUnderlyingType() )
//                            || InferenceUtils.isAnnotated(declaredUpper.getUpperBound()) ) {
//                        if (typeargTrees == null || typeargTrees.isEmpty()) {
//                            commonAssignmentCheck(declaredUpper.getUpperBound(), typearg,
//                                    toptree,
//                                    "argument.type.incompatible", false);
//                        } else {
//                            commonAssignmentCheck(declaredUpper.getUpperBound(), typearg,
//                                    typeargTrees.get(typeargs.indexOf(typearg)),
//                                    "generic.argument.invalid", false);
//                        }
//                    }
//                }
//
//                AnnotatedTypeMirror taForUpper = typearg;
//                if( typearg instanceof AnnotatedTypeVariable ) {
//                    final AnnotatedTypeVariable typeArgTv = (AnnotatedTypeVariable) typearg;
//                    final TypeParameterElement typeArgTpElem = (TypeParameterElement) typeArgTv.getUnderlyingType().asElement();
//                    taForUpper = ((InferenceChecker) checker).typeParamElemToUpperBound().apply(typeArgTpElem);
//                }
//
//                if( infer ) {
//                    //InferenceAnnotationUtils.traverseAndSubtype(taForUpper, declaredUpper.getUpperBound());
//                    //InferenceAnnotationUtils.traverseAndSubtype(declaredLower, typearg);
//                    final SubtypingVisitor subtypingVisitor = new SubtypingVisitor();
//
//                    subtypingVisitor.visitTopLevel( declaredUpper.getUpperBound(), taForUpper );
//                    subtypingVisitor.getResult().addTo( InferenceMain.constraintMgr() );
//                }
//
//                if (!declaredUpper.getAnnotations().isEmpty() && !InferenceMain.isPerformingFlow()) {
//                    // BaseTypeVisitor does
//                    // if (!typearg.getAnnotations().equals(typeVar.getAnnotationsOnTypeVar())) {
//                    // Instead, we go through all annotations and create equality constraints for them.
//
//                    java.util.Set<AnnotationMirror> taannos = typearg.getAnnotations();
//                    java.util.Set<AnnotationMirror> tvannos = typeVar.getAnnotations();
//
//                    for (AnnotationMirror ta : taannos) {
//                        constraintMgr().addSubtypeConstraint( ta, declaredUpper.getAnnotation(VarAnnot.class) );
//                        constraintMgr().addSubtypeConstraint( declaredLower.getAnnotation(VarAnnot.class), ta );
//
//                        for (AnnotationMirror tv : tvannos) {
//                            if (InferenceMain.DEBUG(this)) {
//                                System.out.println("InferenceVisitor::checkTypeArguments: Subtype constraint constructor invocation.");
//                            }
//
//                            constraintMgr().addSubtypeConstraint(tv, ta);
//                        }
//                    }
//                }
//            }
//        }
    }


    // =========== Copied from BaseTypeVisitor ============= //

    // **********************************************************************
    // Responsible for updating the factory for the location (for performance)
    // **********************************************************************

    /**
     * Constructs an instance of the appropriate type factory for the
     * implemented type system.
     *
     * The default implementation uses the checker naming convention to create
     * the appropriate type factory.  If no factory is found, it returns
     * {@link BaseAnnotatedTypeFactory}.  It reflectively invokes the
     * constructor that accepts this checker and compilation unit tree
     * (in that order) as arguments.
     *
     * Subclasses have to override this method to create the appropriate
     * visitor if they do not follow the checker naming convention.
     *
     * @return the appropriate type factory
     */
//    @SuppressWarnings("unchecked") // unchecked cast to type variable
//    protected Factory createTypeFactory() {
//        if( infer ) {
//            final BaseAnnotatedTypeFactory realFactory = createRealTypeFactory();
//            return (Factory) new InferenceAnnotatedTypeFactory( (InferenceChecker) checker, InferenceMain.getRealChecker().withCombineConstraints(), realFactory );
//        } else {
//            return (Factory) createRealTypeFactory();
//        }
//    }
//
//    protected BaseAnnotatedTypeFactory createRealTypeFactory() {
//        // Try to reflectively load the type factory.
//        Class<?> checkerClass = checker.getClass();
//        while (checkerClass != BaseTypeChecker.class) {
//            final String classToLoad =
//                    checkerClass.getName().replace("Checker", "AnnotatedTypeFactory")
//                            .replace("Subchecker", "AnnotatedTypeFactory");
//
//            AnnotatedTypeFactory result = BaseTypeChecker.invokeConstructorFor(classToLoad,
//                    new Class<?>[] { BaseTypeChecker.class },
//                    new Object[] { checker });
//            if (result != null) {
//                return (BaseAnnotatedTypeFactory) result;
//            }
//            checkerClass = checkerClass.getSuperclass();
//        }
//        return new BaseAnnotatedTypeFactory(checker);
//
//    }

    public final Factory getTypeFactory() {
        return atypeFactory;
    }

    // **********************************************************************
    // Responsible for updating the factory for the location (for performance)
    // **********************************************************************

    @Override
    public Void visit(CompilationUnitTree root, TreePath path, Void p) {
        atypeFactory.setRoot(root);
        return super.visit(root, path, p);
    }

    @Override
    public Void scan(Tree tree, Void p) {
        if (tree != null && getCurrentPath() != null)
            this.visitorState.setPath(new TreePath(getCurrentPath(), tree));
        return super.scan(tree, p);
    }

    @Override
    public Void visitClass(ClassTree node, Void p) {
        if (checker.shouldSkipDefs(node)) {
            // Not "return super.visitClass(node, p);" because that would
            // recursively call visitors on subtrees; we want to skip the
            // class entirely.
            return null;
        }

        AnnotatedDeclaredType preACT = visitorState.getClassType();
        ClassTree preCT = visitorState.getClassTree();
        AnnotatedDeclaredType preAMT = visitorState.getMethodReceiver();
        MethodTree preMT = visitorState.getMethodTree();
        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();

        // For flow-sensitive type checking, it's significant that we get the
        // annotated type of the ClassTree before checking the type of any
        // code within the class.  The call below causes flow analysis to
        // be run over the class.  See GenericAnnotatedTypeFactory
        // .annotateImplicitWithFlow where analysis is performed.
        visitorState.setClassType(atypeFactory.getAnnotatedType(node));
        visitorState.setClassTree(node);
        visitorState.setMethodReceiver(null);
        visitorState.setMethodTree(null);
        visitorState.setAssignmentContext(null);

        try {
            if (!TreeUtils.hasExplicitConstructor(node)) {
                checkDefaultConstructor(node);
            }

            /* Visit the extends and implements clauses.
             * The superclass also visits them, but only calls visitParameterizedType, which
             * looses a main modifier.
             */
            Tree ext = node.getExtendsClause();
            if (ext != null) {
                validateTypeOf(ext);
            }

            List<? extends Tree> impls = node.getImplementsClause();
            if (impls != null) {
                for (Tree im : impls) {
                    validateTypeOf(im);
                }
            }

            return super.visitClass(node, p);
        } finally {
            this.visitorState.setClassType(preACT);
            this.visitorState.setClassTree(preCT);
            this.visitorState.setMethodReceiver(preAMT);
            this.visitorState.setMethodTree(preMT);
            this.visitorState.setAssignmentContext(preAssCtxt);
        }
    }

    protected void checkDefaultConstructor(ClassTree node) { }

    /**
     * Performs pseudo-assignment check: checks that the method obeys override
     * and subtype rules to all overridden methods.
     *
     * The override rule specifies that a method, m1, may override a method
     * m2 only if:
     * <ul>
     *  <li> m1 return type is a subtype of m2 </li>
     *  <li> m1 receiver type is a supertype of m2 </li>
     *  <li> m1 parameters are supertypes of corresponding m2 parameters </li>
     * </ul>
     *
     * Also, it issues a "missing.this" error for static method annotated
     * receivers.
     */
    @Override
    public Void visitMethod(MethodTree node, Void p) {

        // We copy the result from getAnnotatedType to ensure that
        // circular types (e.g. K extends Comparable<K>) are represented
        // by circular AnnotatedTypeMirrors, which avoids problems with
        // later checks.
        // TODO: Find a cleaner way to ensure circular AnnotatedTypeMirrors.
        AnnotatedExecutableType methodType = AnnotatedTypes.deepCopy(atypeFactory.getAnnotatedType(node));
        AnnotatedDeclaredType preMRT = visitorState.getMethodReceiver();
        MethodTree preMT = visitorState.getMethodTree();
        visitorState.setMethodReceiver(methodType.getReceiverType());
        visitorState.setMethodTree(node);
        ExecutableElement methodElement = TreeUtils.elementFromDeclaration(node);

        try {
            if (InternalUtils.isAnonymousConstructor(node)) {
                // We shouldn't dig deeper
                return null;
            }

            // check method purity if needed
            {
                boolean anyPurityAnnotation = PurityUtils.hasPurityAnnotation(
                        atypeFactory, node);
                boolean checkPurityAlways = checker.hasOption("suggestPureMethods");
                boolean enablePurity = checker.hasOption("enablePurity");

                if (enablePurity && (anyPurityAnnotation || checkPurityAlways)) {
                    // check "no" purity
                    List<Pure.Kind> kinds = PurityUtils.getPurityKinds(atypeFactory, node);

                    // @Deterministic makes no sense for a void method or constructor
                    boolean isDeterministic = kinds.contains(Pure.Kind.DETERMINISTIC);
                    if (isDeterministic) {
                        if (TreeUtils.isConstructor(node)) {
                            checker.report(Result.warning("purity.deterministic.constructor"),
                                    node);
                        } else if (InternalUtils.typeOf(node.getReturnType()).getKind() == TypeKind.VOID) {
                            checker.report(Result.warning("purity.deterministic.void.method"),
                                    node);
                        }
                    }

                    // Report errors if necessary.
                    PurityResult r = PurityChecker.checkPurity(node.getBody(),
                            atypeFactory, checker.hasOption("assumeSideEffectFree"));
                    if (!r.isPure(kinds)) {
                        reportPurityErrors(r, node, kinds);
                    }

                    // Issue a warning if the method is pure, but not annotated
                    // as such (if the feature is activated).
                    if (checkPurityAlways) {
                        Collection<Pure.Kind> additionalKinds = new HashSet<>(
                                r.getTypes());
                        additionalKinds.removeAll(kinds);
                        if (TreeUtils.isConstructor(node)) {
                            additionalKinds.remove(Pure.Kind.DETERMINISTIC);
                        }
                        if (!additionalKinds.isEmpty()) {
                            if (additionalKinds.size() == 2) {
                                checker.report(
                                        Result.warning("purity.more.pure",
                                                node.getName()), node);
                            } else if (additionalKinds.contains(Pure.Kind.SIDE_EFFECT_FREE)) {
                                checker.report(
                                        Result.warning("purity.more.sideeffectfree",
                                                node.getName()), node);
                            } else if (additionalKinds.contains(Pure.Kind.DETERMINISTIC)) {
                                checker.report(
                                        Result.warning("purity.more.deterministic",
                                                node.getName()), node);
                            } else {
                                assert false : "BaseTypeVisitor reached undesirable state";
                            }
                        }
                    }
                }
            }

            // Passing the whole method/constructor validates the return type
            validateTypeOf(node);

            // Validate types in throws clauses
            for (ExpressionTree thr : node.getThrows()) {
                validateTypeOf(thr);
            }

            AnnotatedDeclaredType enclosingType = (AnnotatedDeclaredType) atypeFactory
                    .getAnnotatedType(methodElement.getEnclosingElement());

            // Find which method this overrides!
            Map<AnnotatedDeclaredType, ExecutableElement> overriddenMethods = AnnotatedTypes
                    .overriddenMethods(elements, atypeFactory, methodElement);
            for (Map.Entry<AnnotatedDeclaredType, ExecutableElement> pair : overriddenMethods
                    .entrySet()) {
                AnnotatedDeclaredType overriddenType = pair.getKey();
                AnnotatedExecutableType overriddenMethod = AnnotatedTypes
                        .asMemberOf(types, atypeFactory, overriddenType,
                                pair.getValue());
                checkOverride(node, enclosingType, overriddenMethod,
                        overriddenType, p);
            }
            return super.visitMethod(node, p);
        } finally {
            boolean abstractMethod = methodElement.getModifiers().contains(Modifier.ABSTRACT);

            if (!abstractMethod) {
                // check postcondition annotations
                checkPostconditions(node, methodElement);

                // check conditional method postcondition
                checkConditionalPostconditions(node, methodElement);
            }

            // check well-formedness of pre/postcondition
            checkPreconditionsConsistency(node, methodElement);
            checkPostconditionsConsistency(node, methodElement);
            checkConditionalPostconditionsConsistency(node, methodElement);

            visitorState.setMethodReceiver(preMRT);
            visitorState.setMethodTree(preMT);
        }
    }

    /**
     * Reports errors found during purity checking.
     */
    protected void reportPurityErrors(PurityResult result, MethodTree node,
            Collection<Pure.Kind> expectedTypes) {
        assert !result.isPure(expectedTypes);
        Collection<Pure.Kind> t = EnumSet.copyOf(expectedTypes);
        t.removeAll(result.getTypes());
        if (t.contains(Pure.Kind.DETERMINISTIC)
                || t.contains(Pure.Kind.SIDE_EFFECT_FREE)) {
            String msgPrefix = "purity.not.deterministic.not.sideeffectfree.";
            if (!t.contains(Pure.Kind.SIDE_EFFECT_FREE)) {
                msgPrefix = "purity.not.deterministic.";
            } else if (!t.contains(Pure.Kind.DETERMINISTIC)) {
                msgPrefix = "purity.not.sideeffectfree.";
            }
            for (Pair<Tree, String> r: result.getNotBothReasons()) {
                @SuppressWarnings("CompilerMessages")
                /*@CompilerMessageKey*/ String msg = msgPrefix + r.second;
                checker.report(Result.failure(msg), r.first);
            }
            if (t.contains(Pure.Kind.SIDE_EFFECT_FREE)) {
                for (Pair<Tree, String> r: result.getNotSeFreeReasons()) {
                    @SuppressWarnings("CompilerMessages")
                    /*@CompilerMessageKey*/ String msg = "purity.not.sideeffectfree." + r.second;
                    checker.report(Result.failure(msg), r.first);
                }
            }
            if (t.contains(Pure.Kind.DETERMINISTIC)) {
                for (Pair<Tree, String> r: result.getNotDetReasons()) {
                    @SuppressWarnings("CompilerMessages")
                    /*@CompilerMessageKey*/ String msg = "purity.not.deterministic." + r.second;
                    checker.report(Result.failure(msg), r.first);
                }
            }
        }
    }

    /**
     * Checks all (non-conditional) postcondition on the method {@code node}
     * with element {@code methodElement}.
     */
    protected void checkPostconditions(MethodTree node,
            ExecutableElement methodElement) {
        FlowExpressionContext flowExprContext = null;
        Set<Pair<String, String>> postconditions = contractsUtils
                .getPostconditions(methodElement);

        for (Pair<String, String> p : postconditions) {
            String expression = p.first;
            AnnotationMirror annotation = AnnotationUtils.fromName(elements,
                    p.second);

            // Only check if the postcondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(annotation)) {
                continue;
            }
            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(node,
                                getCurrentPath(), atypeFactory);
            }

            FlowExpressions.Receiver expr = null;
            try {
                // TODO: currently, these expressions are parsed at the
                // declaration (i.e. here) and for every use. this could be
                // optimized to store the result the first time. (same for
                // other annotations)
                expr = FlowExpressionParseUtil.parse(expression,
                        flowExprContext, getCurrentPath());

                CFAbstractStore<?, ?> exitStore = atypeFactory
                        .getRegularExitStore(node);
                if (exitStore == null) {
                    // if there is no regular exitStore, then the method
                    // cannot reach the regular exit and there is no need to
                    // check anything
                } else {
                    CFAbstractValue<?> value = exitStore.getValue(expr);
                    AnnotationMirror inferredAnno = value == null ? null
                            : value.getType().getAnnotationInHierarchy(
                                    annotation);
                    if (!checkContract(expr, annotation, inferredAnno, exitStore)) {
                        checker.report(
                                Result.failure("contracts.postcondition.not.satisfied", expr.toString()),
                                node);
                    }
                }

            } catch (FlowExpressionParseException e) {
                // report errors here
                checker.report(e.getResult(), node);
            }
        }
    }

    /**
     * Checks all (non-conditional) postcondition on the method {@code node}
     * with element {@code methodElement} for consistency.
     */
    protected void checkPostconditionsConsistency(MethodTree node,
            ExecutableElement methodElement) {
        FlowExpressionContext flowExprContext = null;
        Set<Pair<String, String>> postconditions = contractsUtils
                .getPostconditions(methodElement);

        for (Pair<String, String> p : postconditions) {
            String expression = p.first;
            AnnotationMirror annotation = AnnotationUtils.fromName(elements,
                    p.second);

            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(node,
                                getCurrentPath(), atypeFactory);
            }

            // Only check if the postcondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(annotation)) {
                continue;
            }
            try {
                FlowExpressionParseUtil.parse(expression,
                        flowExprContext, getCurrentPath());
            } catch (FlowExpressionParseException e) {
                // ignore expressions that do not parse
                continue;
            }
            checkFlowExprParameters(methodElement, expression);
        }
    }

    /**
     * Checks all conditional postcondition on the method {@code node} with
     * element {@code methodElement}.
     */
    protected void checkConditionalPostconditions(MethodTree node,
            ExecutableElement methodElement) {
        FlowExpressionContext flowExprContext = null;
        Set<Pair<String, Pair<Boolean, String>>> conditionalPostconditions = contractsUtils
                .getConditionalPostconditions(methodElement);

        for (Pair<String, Pair<Boolean, String>> p : conditionalPostconditions) {
            String expression = p.first;
            boolean result = p.second.first;
            AnnotationMirror annotation = AnnotationUtils.fromName(elements,
                    p.second.second);

            // Only check if the postcondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(annotation)) {
                continue;
            }
            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(node,
                                getCurrentPath(), atypeFactory);
            }

            FlowExpressions.Receiver expr = null;
            try {
                // TODO: currently, these expressions are parsed at the
                // declaration (i.e. here) and for every use. this could be
                // optimized to store the result the first time. (same for
                // other annotations)
                expr = FlowExpressionParseUtil.parse(expression,
                        flowExprContext, getCurrentPath());

                // check return type of method
                boolean booleanReturnType = TypesUtils
                        .isBooleanType(InternalUtils.typeOf(node
                                .getReturnType()));
                if (!booleanReturnType) {
                    checker.report(
                            Result.failure("contracts.conditional.postcondition.invalid.returntype"),
                            node);
                    // No reason to go ahead with further checking. The
                    // annotation is invalid.
                    continue;
                }

                List<?> returnStatements = atypeFactory
                        .getReturnStatementStores(node);
                for (Object rt : returnStatements) {
                    @SuppressWarnings("unchecked")
                    Pair<ReturnNode, TransferResult<? extends CFAbstractValue<?>, ? extends CFAbstractStore<?, ?>>> r = (Pair<ReturnNode, TransferResult<? extends CFAbstractValue<?>, ? extends CFAbstractStore<?, ?>>>) rt;
                    ReturnNode returnStmt = r.first;
                    if (r.second == null) {
                        // Unreachable return statements have no stores, but
                        // there
                        // is no need to check them.
                        continue;
                    }
                    Node retValNode = returnStmt.getResult();
                    Boolean retVal = retValNode instanceof BooleanLiteralNode ? ((BooleanLiteralNode) retValNode)
                            .getValue() : null;
                    CFAbstractStore<?, ?> exitStore;
                    if (result) {
                        exitStore = r.second.getThenStore();
                    } else {
                        exitStore = r.second.getElseStore();
                    }
                    CFAbstractValue<?> value = exitStore.getValue(expr);
                    // don't check if return statement certainly does not
                    // match 'result'. at the moment, this means the result
                    // is a boolean literal
                    if (retVal == null || retVal == result) {
                        AnnotationMirror inferredAnno = value == null ? null
                                : value.getType().getAnnotationInHierarchy(
                                        annotation);
                        if (!checkContract(expr, annotation, inferredAnno, exitStore)) {
                            checker.report(
                                    Result.failure("contracts.conditional.postcondition.not.satisfied", expr.toString()),
                                    returnStmt.getTree());
                        }
                    }
                }

            } catch (FlowExpressionParseException e) {
                // report errors here
                checker.report(e.getResult(), node);
            }
        }
    }

    /**
     * Checks all conditional postcondition on the method with element
     * {@code methodElement} for consistency.
     */
    protected void checkConditionalPostconditionsConsistency(MethodTree node,
            ExecutableElement methodElement) {
        FlowExpressionContext flowExprContext = null;
        Set<Pair<String, Pair<Boolean, String>>> conditionalPostconditions = contractsUtils
                .getConditionalPostconditions(methodElement);

        for (Pair<String, Pair<Boolean, String>> p : conditionalPostconditions) {
            String expression = p.first;
            AnnotationMirror annotation = AnnotationUtils.fromName(elements,
                    p.second.second);

            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(node,
                                getCurrentPath(), atypeFactory);
            }

            // Only check if the postcondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(annotation)) {
                continue;
            }
            try {
                FlowExpressionParseUtil.parse(expression,
                        flowExprContext, getCurrentPath());
            } catch (FlowExpressionParseException e) {
                // ignore expressions that do not parse
                continue;
            }
            checkFlowExprParameters(methodElement, expression);

        }
    }

    /**
     * Check that the parameters used in {@code stringExpr} are final for method
     * {@code method}.
     */
    protected void checkFlowExprParameters(ExecutableElement method, String stringExpr) {
        // check that all parameters used in the expression are
        // final, so that they cannot be modified
        List<Integer> parameterIndices = FlowExpressionParseUtil.parameterIndices(stringExpr);
        for (Integer idx : parameterIndices) {
            VariableElement parameter = method.getParameters().get(idx - 1);
            if (!ElementUtils.isEffectivelyFinal(parameter)) {
                checker.report(
                        Result.failure("flowexpr.parameter.not.final",
                                "#" + idx, stringExpr), method);
            }
        }
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree node, Void p) {
        validateTypeOf(node);
        // Check the bounds here and not with every TypeParameterTree.
        // For the latter, we only need to check annotations on the type variable itself.
        // Why isn't this covered by the super call?
        for (Tree tpb : node.getBounds()) {
            validateTypeOf(tpb);
        }
        return super.visitTypeParameter(node, p);
    }

    // **********************************************************************
    // Assignment checkers and pseudo-assignments
    // **********************************************************************

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();
        visitorState.setAssignmentContext(Pair.of((Tree) node, atypeFactory.getAnnotatedType(node)));

        try {
            boolean valid = validateTypeOf(node);
            // If there's no assignment in this variable declaration, skip it.
            if (valid && node.getInitializer() != null) {
                commonAssignmentCheck(node, node.getInitializer(),
                        "assignment.type.incompatible");
            }
            return super.visitVariable(node, p);
        } finally {
            visitorState.setAssignmentContext(preAssCtxt);
        }
    }

    /**
     * Performs two checks: subtyping and assignability checks, using
     * {@link #commonAssignmentCheck(Tree, ExpressionTree, String)}.
     *
     * If the subtype check fails, it issues a "assignment.type.incompatible" error.
     */
    @Override
    public Void visitAssignment(AssignmentTree node, Void p) {
        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();
        visitorState.setAssignmentContext(Pair.of((Tree) node.getVariable(), atypeFactory.getAnnotatedType(node.getVariable())));
        try {
            commonAssignmentCheck(node.getVariable(), node.getExpression(),
                    "assignment.type.incompatible");
            return super.visitAssignment(node, p);
        } finally {
            visitorState.setAssignmentContext(preAssCtxt);
        }
    }

    /**
     * Performs a subtype check, to test whether the node expression
     * iterable type is a subtype of the variable type in the enhanced for
     * loop.
     *
     * If the subtype check fails, it issues a "enhancedfor.type.incompatible" error.
     */
    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        AnnotatedTypeMirror var = atypeFactory.getAnnotatedType(node.getVariable());
        AnnotatedTypeMirror iterableType =
            atypeFactory.getAnnotatedType(node.getExpression());
        AnnotatedTypeMirror iteratedType =
            AnnotatedTypes.getIteratedType(checker.getProcessingEnvironment(), atypeFactory, iterableType);
        boolean valid = validateTypeOf(node.getVariable());
        if (valid) {
            commonAssignmentCheck(var, iteratedType, node.getExpression(),
                    "enhancedfor.type.incompatible", true);
        }
        return super.visitEnhancedForLoop(node, p);
    }

    /**
     * Performs a method invocation check.
     *
     * An invocation of a method, m, on the receiver, r is valid only if:
     * <ul>
     *  <li> passed arguments are subtypes of corresponding m parameters </li>
     *  <li> r is a subtype of m receiver type </li>
     *  <li> if m is generic, passed type arguments are subtypes
     *      of m type variables </li>
     * </ul>
     */
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {

        // Skip calls to the Enum constructor (they're generated by javac and
        // hard to check), also see CFGBuilder.visitMethodInvocation.
        if (TreeUtils.isEnumSuper(node))
            return super.visitMethodInvocation(node, p);

        if (shouldSkipUses(node))
            return super.visitMethodInvocation(node, p);

        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = atypeFactory.methodFromUse(node);
        AnnotatedExecutableType invokedMethod = mfuPair.first;
        List<AnnotatedTypeMirror> typeargs = mfuPair.second;

        List<AnnotatedTypeParameterBounds> paramBounds = new ArrayList<>();
        for (AnnotatedTypeVariable param : invokedMethod.getTypeVariables()) {
            paramBounds.add(param.getBounds());
        }

        checkTypeArguments(node, paramBounds, typeargs, node.getTypeArguments());

        List<AnnotatedTypeMirror> params =
            AnnotatedTypes.expandVarArgs(atypeFactory, invokedMethod, node.getArguments());
        checkArguments(params, node.getArguments());

        if (isVectorCopyInto(invokedMethod)) {
            typeCheckVectorCopyIntoArgument(node, params);
        }

        ExecutableElement invokedMethodElement = invokedMethod.getElement();
        if (!ElementUtils.isStatic(invokedMethodElement)
                && !TreeUtils.isSuperCall(node)) {
            checkMethodInvocability(invokedMethod, node);
        }

        // check precondition annotations
        checkPreconditions(node, invokedMethodElement);

        // Do not call super, as that would observe the arguments without
        // a set assignment context.
        scan(node.getMethodSelect(), p);
        return null; // super.visitMethodInvocation(node, p);
    }

    /**
     * Checks all the preconditions of the method invocation {@code tree} with
     * element {@code invokedMethodElement}.
     */
    protected void checkPreconditions(MethodInvocationTree tree,
            ExecutableElement invokedMethodElement) {
        Set<Pair<String, String>> preconditions = contractsUtils
                .getPreconditions(invokedMethodElement);
        FlowExpressionContext flowExprContext = null;

        for (Pair<String, String> p : preconditions) {
            String expression = p.first;
            AnnotationMirror anno = AnnotationUtils
                    .fromName(elements, p.second);

            // Only check if the precondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(anno)) {
                return;
            }
            if (flowExprContext == null) {
                Node nodeNode = atypeFactory.getNodeForTree(tree);
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForUse(
                                (MethodInvocationNode) nodeNode, atypeFactory);
            }

            FlowExpressions.Receiver expr = null;
            try {
                expr = FlowExpressionParseUtil.parse(expression,
                        flowExprContext, getCurrentPath());

                CFAbstractStore<?, ?> store = atypeFactory.getStoreBefore(tree);
                CFAbstractValue<?> value = store.getValue(expr);

                AnnotationMirror inferredAnno = value == null ? null : value
                        .getType().getAnnotationInHierarchy(anno);
                if (!checkContract(expr, anno, inferredAnno, store)) {
                    checker.report(Result.failure(
                            "contracts.precondition.not.satisfied",
                            expr.toString()), tree);
                }
            } catch (FlowExpressionParseException e) {
                // errors are reported at declaration site
            }
        }
    }

    /**
     * Checks all the preconditions of the method with element
     * {@code methodElement} for consistency.
     */
    protected void checkPreconditionsConsistency(MethodTree node,
            ExecutableElement methodElement) {
        FlowExpressionContext flowExprContext = null;
        Set<Pair<String, String>> preconditions = contractsUtils
                .getPreconditions(methodElement);

        for (Pair<String, String> p : preconditions) {
            String expression = p.first;
            AnnotationMirror anno = AnnotationUtils
                    .fromName(elements, p.second);

            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(node,
                                getCurrentPath(), atypeFactory);
            }

            // Only check if the precondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(anno)) {
                return;
            }
            try {
                FlowExpressionParseUtil.parse(expression, flowExprContext,
                        getCurrentPath());
            } catch (FlowExpressionParseException e) {
                // ignore expressions that do not parse
                continue;
            }
            checkFlowExprParameters(methodElement, expression);
        }
    }

    /**
     * Returns true if and only if {@code inferredAnnotation} is valid for a
     * given expression to match the {@code necessaryAnnotation}.
     *
     * <p>
     * By default, {@code inferredAnnotation} must be a subtype of
     * {@code necessaryAnnotation}, but subclasses might override this behavior.
     */
    protected boolean checkContract(Receiver expr,
            AnnotationMirror necessaryAnnotation,
            AnnotationMirror inferredAnnotation, CFAbstractStore<?, ?> store) {
        return !(inferredAnnotation == null || !atypeFactory
                .getQualifierHierarchy().isSubtype(inferredAnnotation,
                        necessaryAnnotation));
    }

    // Handle case Vector.copyInto()
    private final AnnotatedDeclaredType vectorType;

    /**
     * Returns true if the method symbol represents {@code Vector.copyInto}
     */
    protected boolean isVectorCopyInto(AnnotatedExecutableType method) {
        ExecutableElement elt = method.getElement();
        if (elt.getSimpleName().contentEquals("copyInto")
                && elt.getParameters().size() == 1)
            return true;

        return false;
    }

    /**
     * Type checks the method arguments of {@code Vector.copyInto()}.
     *
     * The Checker Framework special-cases the method invocation, as it is
     * type safety cannot be expressed by Java's type system.
     *
     * For a Vector {@code v} of type {@code Vectory<E>}, the method
     * invocation {@code v.copyInto(arr)} is type-safe iff {@code arr}
     * is a array of type {@code T[]}, where {@code T} is a subtype of
     * {@code E}.
     *
     * In other words, this method checks that the type argument of the
     * receiver method is a subtype of the component type of the passed array
     * argument.
     *
     * @param node   a method invocation of {@code Vector.copyInto()}
     * @param params the types of the parameters of {@code Vectory.copyInto()}
     *
     */
    protected void typeCheckVectorCopyIntoArgument(MethodInvocationTree node,
            List<? extends AnnotatedTypeMirror> params) {
        assert params.size() == 1 : "invalid no. of parameters " + params + " found for method invocation " + node;
        assert node.getArguments().size() == 1 : "invalid no. of arguments in method invocation " + node;

        AnnotatedTypeMirror passed = atypeFactory.getAnnotatedType(node.getArguments().get(0));
        AnnotatedArrayType passedAsArray = (AnnotatedArrayType)passed;

        AnnotatedTypeMirror receiver = atypeFactory.getReceiverType(node);
        AnnotatedDeclaredType receiverAsVector = (AnnotatedDeclaredType) AnnotatedTypes
                .asSuper(checker.getProcessingEnvironment().getTypeUtils(),
                        atypeFactory, receiver, vectorType);
        if (receiverAsVector == null
                || receiverAsVector.getTypeArguments().isEmpty())
            return;

        commonAssignmentCheck(
                passedAsArray.getComponentType(),
                receiverAsVector.getTypeArguments().get(0),
                node.getArguments().get(0),
                "vector.copyinto.type.incompatible",
                false);
    }

    /**
     * Performs a new class invocation check.
     *
     * An invocation of a constructor, c, is valid only if:
     * <ul>
     *  <li> passed arguments are subtypes of corresponding c parameters </li>
     *  <li> if c is generic, passed type arguments are subtypes
     *      of c type variables </li>
     * </ul>
     */
    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (checker.shouldSkipUses(InternalUtils.constructor(node)))
            return super.visitNewClass(node, p);

        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> fromUse = atypeFactory.constructorFromUse(node);
        AnnotatedExecutableType constructor = fromUse.first;
        List<AnnotatedTypeMirror> typeargs = fromUse.second;

        List<? extends ExpressionTree> passedArguments = node.getArguments();
        List<AnnotatedTypeMirror> params =
            AnnotatedTypes.expandVarArgs(atypeFactory, constructor, passedArguments);

        checkArguments(params, passedArguments);

        // Get the constructor type.
        // TODO: What is the difference between "type" and "constructor"?
        // Using "constructor" seems to work equally well...
        // AnnotatedExecutableType type =
        //   atypeFactory.getAnnotatedType(InternalUtils.constructor(node));

        List<AnnotatedTypeParameterBounds> paramBounds = new ArrayList<>();
        for (AnnotatedTypeVariable param : constructor.getTypeVariables()) {
            paramBounds.add(param.getBounds());
        }

        checkTypeArguments(node, paramBounds, typeargs, node.getTypeArguments());

        boolean valid = validateTypeOf(node);

        if (valid) {
            AnnotatedDeclaredType dt = atypeFactory.getAnnotatedType(node);
            checkConstructorInvocation(dt, constructor, node);
        }

        return super.visitNewClass(node, p);
    }

    /**
     * Checks that the type of the return expression is a subtype of the
     * enclosing method required return type.  If not, it issues a
     * "return.type.incompatible" error.
     */
    @Override
    public Void visitReturn(ReturnTree node, Void p) {
        // Don't try to check return expressions for void methods.
        if (node.getExpression() == null)
            return super.visitReturn(node, p);

        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();
        try {
            MethodTree enclosingMethod =
                    TreeUtils.enclosingMethod(getCurrentPath());

            boolean valid = validateTypeOf(enclosingMethod);

            if (valid) {
                AnnotatedTypeMirror ret = atypeFactory.getMethodReturnType(enclosingMethod, node);
                visitorState.setAssignmentContext(Pair.of((Tree) node, ret));

                commonAssignmentCheck(ret, node.getExpression(),
                        "return.type.incompatible", false);
            }
            return super.visitReturn(node, p);
        } finally {
            visitorState.setAssignmentContext(preAssCtxt);
        }
    }

    /** TODO: something similar to visitReturn should be done.
    public Void visitThrow(ThrowTree node, Void p) {
        return super.visitThrow(node, p);
    }
    */

    /**
     * Ensure that the annotation arguments comply to their declarations. This
     * needs some special casing, as annotation arguments form special trees.
     */
    @Override
    public Void visitAnnotation(AnnotationTree node, Void p) {
        // TODO: Re-enable this
        if (true) {
            return null;
        }

        List<? extends ExpressionTree> args = node.getArguments();
        if (args.isEmpty()) {
            // Nothing to do if there are no annotation arguments.
            return null;
        }

        Element anno = TreeInfo.symbol((JCTree) node.getAnnotationType());
        if (anno.toString().equals(DefaultQualifier.class.getName()) ||
                anno.toString().equals(SuppressWarnings.class.getName())) {
            // Skip these two annotations, as we don't care about the
            // arguments to them.
            return null;
        }

        // Mapping from argument simple name to its annotated type.
        Map<String, AnnotatedTypeMirror> annoTypes = new HashMap<String, AnnotatedTypeMirror>();
        for (Element encl : ElementFilter.methodsIn(anno.getEnclosedElements())) {
            AnnotatedExecutableType exeatm = (AnnotatedExecutableType) atypeFactory.getAnnotatedType(encl);
            AnnotatedTypeMirror retty = exeatm.getReturnType();
            annoTypes.put(encl.getSimpleName().toString(), retty);
        }

        for (ExpressionTree arg : args) {
            if (!(arg instanceof AssignmentTree)) {
                // TODO: when can this happen?
                continue;
            }

            AssignmentTree at = (AssignmentTree) arg;
            // Ensure that we never ask for the annotated type of an annotation, because
            // we don't have a type for annotations.
            if (at.getExpression().getKind() == Tree.Kind.ANNOTATION) {
                visitAnnotation((AnnotationTree) at.getExpression(), p);
                continue;
            }
            if (at.getExpression().getKind() == Tree.Kind.NEW_ARRAY) {
                NewArrayTree nat = (NewArrayTree) at.getExpression();
                boolean isAnno = false;
                for (ExpressionTree init : nat.getInitializers()) {
                    if (init.getKind() == Tree.Kind.ANNOTATION) {
                        visitAnnotation((AnnotationTree) init, p);
                        isAnno = true;
                    }
                }
                if (isAnno) {
                    continue;
                }
            }

            AnnotatedTypeMirror expected = annoTypes.get(at.getVariable().toString());
            Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();

            {
                // Determine and set the new assignment context.
                ExpressionTree var = at.getVariable();
                assert var instanceof IdentifierTree : "Expected IdentifierTree as context. Found: " + var;
                AnnotatedTypeMirror meth = atypeFactory.getAnnotatedType(var);
                assert meth instanceof AnnotatedExecutableType : "Expected AnnotatedExecutableType as context. Found: " + meth;
                AnnotatedTypeMirror newctx = ((AnnotatedExecutableType) meth).getReturnType();
                visitorState.setAssignmentContext(Pair.<Tree, AnnotatedTypeMirror>of((Tree) null, newctx));
            }

            try {
                AnnotatedTypeMirror actual = atypeFactory.getAnnotatedType(at.getExpression());
                if (expected.getKind() != TypeKind.ARRAY) {
                    // Expected is not an array -> direct comparison.
                    commonAssignmentCheck(expected, actual, at.getExpression(),
                            "annotation.type.incompatible", false);
                } else {
                    if (actual.getKind() == TypeKind.ARRAY) {
                        // Both actual and expected are arrays.
                        commonAssignmentCheck(expected, actual, at.getExpression(),
                                "annotation.type.incompatible", false);
                    } else {
                        // The declaration is an array type, but just a single
                        // element is given.
                        commonAssignmentCheck(((AnnotatedArrayType) expected).getComponentType(),
                                actual, at.getExpression(),
                                "annotation.type.incompatible", false);
                    }
                }
            } finally {
                visitorState.setAssignmentContext(preAssCtxt);
            }
        }
        return null;
    }

    /**
     * If the computation of the type of the ConditionalExpressionTree in
     * checkers.types.TypeFromTree.TypeFromExpression.visitConditionalExpression(ConditionalExpressionTree, AnnotatedTypeFactory)
     * is correct, the following checks are redundant.
     * However, let's add another failsafe guard and do the checks.
     */
    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        AnnotatedTypeMirror cond = atypeFactory.getAnnotatedType(node);
        Pair<Tree, AnnotatedTypeMirror> ctx = visitorState.getAssignmentContext();
        Tree assignmentContext = ctx == null ? null : ctx.first;
        boolean isLocalVariableAssignment = false;
        if (assignmentContext != null) {
            if (assignmentContext instanceof VariableTree) {
                isLocalVariableAssignment = assignmentContext instanceof IdentifierTree
                        && !TreeUtils.isFieldAccess(assignmentContext);
            }
            if (assignmentContext instanceof VariableTree) {
                isLocalVariableAssignment = TreeUtils
                        .enclosingMethod(getCurrentPath()) != null;
            }
        }

        this.commonAssignmentCheck(cond, node.getTrueExpression(),
                "conditional.type.incompatible", isLocalVariableAssignment);
        this.commonAssignmentCheck(cond, node.getFalseExpression(),
                "conditional.type.incompatible", isLocalVariableAssignment);
        return super.visitConditionalExpression(node, p);
    }

    // **********************************************************************
    // Check for illegal re-assignment
    // **********************************************************************

    /**
     * Performs assignability check using
     * {@link #checkAssignability(AnnotatedTypeMirror, Tree)}.
     */
    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        if ((node.getKind() == Tree.Kind.PREFIX_DECREMENT) ||
                (node.getKind() == Tree.Kind.PREFIX_INCREMENT) ||
                (node.getKind() == Tree.Kind.POSTFIX_DECREMENT) ||
                (node.getKind() == Tree.Kind.POSTFIX_INCREMENT)) {
            AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(node.getExpression());
            checkAssignability(type, node.getExpression());
        }
        return super.visitUnary(node, p);
    }

    /**
     * Performs assignability check using
     * {@link #checkAssignability(AnnotatedTypeMirror, Tree)}.
     */
    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        commonAssignmentCheck(node.getVariable(), node.getExpression(),
                "compound.assignment.type.incompatible");
        return super.visitCompoundAssignment(node, p);
    }

    // **********************************************************************
    // Check for invalid types inserted by the user
    // **********************************************************************

    @Override
    public Void visitNewArray(NewArrayTree node, Void p) {
        boolean valid = validateTypeOf(node);
        if (valid && node.getType() != null && node.getInitializers() != null) {
            AnnotatedArrayType arrayType = atypeFactory.getAnnotatedType(node);
            checkArrayInitialization(arrayType.getComponentType(),
                    node.getInitializers());
        }

        return super.visitNewArray(node, p);
    }

    /* TODO: add once lambda is fully integrated.
    @Override
    public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
        System.out.println("Params: " + node.getParameters());
        System.out.println("Body: " + node.getBody());
        return super.visitLambdaExpression(node, p);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree node, Void p) {
        // node.getQualifierExpression()
        // node.getTypeArguments()
        return super.visitMemberReference(node, p);
    }*/

    /**
     * Do not override this method!
     * Previously, this method contained some logic, but the main modifier of types was missing.
     * It has been merged with the TypeValidator below.
     * This method doesn't need to do anything, as the type is already validated.
     */
    @Override
    public final Void visitParameterizedType(ParameterizedTypeTree node, Void p) {
        return null; // super.visitParameterizedType(node, p);
    }

    protected void checkTypecastRedundancy(TypeCastTree node, Void p) {
        if (!checker.getLintOption("cast:redundant", false))
            return;

        AnnotatedTypeMirror castType = atypeFactory.getAnnotatedType(node);
        AnnotatedTypeMirror exprType = atypeFactory.getAnnotatedType(node.getExpression());

        if (AnnotatedTypes.areSame(castType, exprType)) {
            checker.report(Result.warning("cast.redundant", castType), node);
        }
    }

    protected void checkTypecastSafety(TypeCastTree node, Void p) {
        if (!checker.getLintOption("cast:unsafe", true))
            return;

        boolean isSubtype = false;

        // We cannot do a simple test of casting, as isSubtypeOf requires
        // the input types to be subtypes according to Java
        AnnotatedTypeMirror castType = atypeFactory.getAnnotatedType(node);
        if (castType.getKind() == TypeKind.DECLARED) {
            // eliminate false positives, where the annotations are
            // implicitly added by the declared type declaration
            AnnotatedDeclaredType castDeclared = (AnnotatedDeclaredType)castType;
            AnnotatedDeclaredType elementType =
                atypeFactory.fromElement((TypeElement)castDeclared.getUnderlyingType().asElement());
            if (AnnotationUtils.areSame(castDeclared.getAnnotations(), elementType.getAnnotations())) {
                isSubtype = true;
            }
        }
        AnnotatedTypeMirror exprType = atypeFactory.getAnnotatedType(node.getExpression());

        if (!isSubtype) {
            if (checker.hasOption("checkCastElementType")) {
                AnnotatedTypeMirror newCastType;
                if (castType.getKind() == TypeKind.TYPEVAR) {
                    newCastType = ((AnnotatedTypeVariable)castType).getEffectiveUpperBound();
                } else {
                    newCastType = castType;
                }
                AnnotatedTypeMirror newExprType;
                if (exprType.getKind() == TypeKind.TYPEVAR) {
                    newExprType = ((AnnotatedTypeVariable)exprType).getEffectiveUpperBound();
                } else {
                    newExprType = exprType;
                }

                isSubtype = atypeFactory.getTypeHierarchy().isSubtype(newExprType, newCastType);
                if (isSubtype) {
                    if (newCastType.getKind() == TypeKind.ARRAY &&
                            newExprType.getKind() != TypeKind.ARRAY) {
                        // Always warn if the cast contains an array, but the expression
                        // doesn't, as in "(Object[]) o" where o is of type Object
                        isSubtype = false;
                    } else if (newCastType.getKind() == TypeKind.DECLARED &&
                            newExprType.getKind() == TypeKind.DECLARED) {
                        int castSize = ((AnnotatedDeclaredType) newCastType).getTypeArguments().size();
                        int exprSize = ((AnnotatedDeclaredType) newExprType).getTypeArguments().size();

                        if (castSize != exprSize) {
                            // Always warn if the cast and expression contain a different number of
                            // type arguments, e.g. to catch a cast from "Object" to "List<@NonNull Object>".
                            // TODO: the same number of arguments actually doesn't guarantee anything.
                            isSubtype = false;
                        }
                    }
                }
            } else {
                // Only check the main qualifiers, ignoring array components and
                // type arguments.
                isSubtype = atypeFactory.getQualifierHierarchy().isSubtype(
                        exprType.getEffectiveAnnotations(),
                        castType.getEffectiveAnnotations());
            }
        }

        if (!isSubtype) {
            checker.report(Result.warning("cast.unsafe", exprType, castType), node);
        }
    }

    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        // validate "node" instead of "node.getType()" to prevent duplicate errors.
        boolean valid = validateTypeOf(node) && validateTypeOf(node.getExpression());
        if (valid) {
            checkTypecastSafety(node, p);
            checkTypecastRedundancy(node, p);
        }
        return super.visitTypeCast(node, p);
        // return scan(node.getExpression(), p);
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree node, Void p) {
        validateTypeOf(node.getType());
        return super.visitInstanceOf(node, p);
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();
        try {
            visitorState.setAssignmentContext(null);
            scan(node.getExpression(), p);
            scan(node.getIndex(), p);
        } finally {
            visitorState.setAssignmentContext(preAssCtxt);
        }
        return null;
    }

    // **********************************************************************
    // Helper methods to provide a single overriding point
    // **********************************************************************

    /**
     * Checks the validity of an assignment (or pseudo-assignment) from a value
     * to a variable and emits an error message (through the compiler's
     * messaging interface) if it is not valid.
     *
     * @param varTree the AST node for the variable
     * @param valueExp the AST node for the value
     * @param errorKey the error message to use if the check fails (must be a
     *        compiler message key, see {@link CompilerMessageKey})
     */
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

    /**
     * Checks the validity of an assignment (or pseudo-assignment) from a value
     * to a variable and emits an error message (through the compiler's
     * messaging interface) if it is not valid.
     *
     * @param varType the annotated type of the variable
     * @param valueExp the AST node for the value
     * @param errorKey the error message to use if the check fails (must be a
     *        compiler message key, see {@link CompilerMessageKey})
     * @param isLocalVariableAssignement
     *            Are we dealing with an assignment and is the lhs a local
     *            variable?
     */
    protected void commonAssignmentCheck(AnnotatedTypeMirror varType,
            ExpressionTree valueExp, /*@CompilerMessageKey*/ String errorKey,
            boolean isLocalVariableAssignement) {
        if (shouldSkipUses(valueExp))
            return;
        if (varType.getKind() == TypeKind.ARRAY
                && valueExp instanceof NewArrayTree
                && ((NewArrayTree) valueExp).getType() == null) {
            AnnotatedTypeMirror compType = ((AnnotatedArrayType) varType).getComponentType();
            NewArrayTree arrayTree = (NewArrayTree) valueExp;
            assert arrayTree.getInitializers() != null : "array initializers are not expected to be null in: " + valueExp;
            checkArrayInitialization(compType, arrayTree.getInitializers());
        }
        if (!validateTypeOf(valueExp)) {
            return;
        }
        AnnotatedTypeMirror valueType = atypeFactory.getAnnotatedType(valueExp);
        assert valueType != null : "null type for expression: " + valueExp;
        commonAssignmentCheck(varType, valueType, valueExp, errorKey,
                isLocalVariableAssignement);
    }

    /**
     * Checks the validity of an assignment (or pseudo-assignment) from a value
     * to a variable and emits an error message (through the compiler's
     * messaging interface) if it is not valid.
     *
     * @param varType the annotated type of the variable
     * @param valueType the annotated type of the value
     * @param valueTree the location to use when reporting the error message
     * @param errorKey the error message to use if the check fails (must be a
     *        compiler message key, see {@link CompilerMessageKey})
     * @param isLocalVariableAssignement
     *            Are we dealing with an assignment and is the lhs a local
     *            variable?
     */
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

    protected void checkArrayInitialization(AnnotatedTypeMirror type,
            List<? extends ExpressionTree> initializers) {
        // TODO: set assignment context like for method arguments?
        // Also in AbstractFlow.
        for (ExpressionTree init : initializers)
            commonAssignmentCheck(type, init,
                    "array.initializer.type.incompatible", false);
    }

    /**
     * Checks that the annotations on the type arguments supplied to a type or a
     * method invocation are within the bounds of the type variables as
     * declared, and issues the "type.argument.type.incompatible" error if they are
     * not.
     *
     * @param toptree the tree for error reporting, only used for inferred type arguments
     * @param typevars the type variables from a class or method declaration
     * @param typeargs the type arguments from the type or method invocation
     * @param typeargTrees the type arguments as trees, used for error reporting
     */
    protected void checkTypeArgumentsCheck(Tree toptree,
            List<? extends AnnotatedTypeVariable> typevars,
            List<? extends AnnotatedTypeMirror> typeargs,
            List<? extends Tree> typeargTrees) {

        // System.out.printf("BaseTypeVisitor.checkTypeArguments: %s, TVs: %s, TAs: %s, TATs: %s\n",
        //         toptree, typevars, typeargs, typeargTrees);

        // If there are no type variables, do nothing.
        if (typevars.isEmpty())
            return;

        assert typevars.size() == typeargs.size() :
            "BaseTypeVisitor.checkTypeArguments: mismatch between type arguments: " +
            typeargs + " and type variables" + typevars;

        Iterator<? extends AnnotatedTypeVariable> varIter = typevars.iterator();
        Iterator<? extends AnnotatedTypeMirror> argIter = typeargs.iterator();

        while (varIter.hasNext()) {

            AnnotatedTypeVariable typeVar = varIter.next();
            AnnotatedTypeMirror typearg = argIter.next();

            // TODO skip wildcards for now to prevent a crash
            if (typearg.getKind() == TypeKind.WILDCARD)
                continue;

            if (typeVar.getEffectiveUpperBound() != null) {
                if (typeargTrees == null || typeargTrees.isEmpty()) {
                    // The type arguments were inferred and we mark the whole method.
                    // The inference fails if we provide invalid arguments,
                    // therefore issue an error for the arguments.
                    // I hope this is less confusing for users.
                    commonAssignmentCheck(typeVar.getEffectiveUpperBound(),
                            typearg, toptree,
                            "type.argument.type.incompatible", false);
                } else {
                    commonAssignmentCheck(typeVar.getEffectiveUpperBound(), typearg,
                            typeargTrees.get(typeargs.indexOf(typearg)),
                            "type.argument.type.incompatible", false);
                }
            }

            // Should we compare lower bounds instead of the annotations on the
            // type variables?
            if (!typeVar.getAnnotations().isEmpty()) {
                if (!typearg.getEffectiveAnnotations().equals(typeVar.getEffectiveAnnotations())) {
                    if (typeargTrees == null || typeargTrees.isEmpty()) {
                        // The type arguments were inferred and we mark the whole method.
                        checker.report(Result.failure("type.argument.type.incompatible",
                                typearg, typeVar),
                                toptree);
                    } else {
                        checker.report(Result.failure("type.argument.type.incompatible",
                                typearg, typeVar),
                                typeargTrees.get(typeargs.indexOf(typearg)));
                    }
                }
            }

        }
    }

    /**
     * Tests whether the method can be invoked using the receiver of the 'node'
     * method invocation, and issues a "method.invocation.invalid" if the
     * invocation is invalid.
     *
     * This implementation tests whether the receiver in the method invocation
     * is a subtype of the method receiver type.
     *
     * @param method    the type of the invoked method
     * @param node      the method invocation node
     */
    protected void checkMethodInvocability(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        if (method.getReceiverType() == null) {
            // Static methods don't have a receiver.
            return;
        }
        if (method.getElement().getKind() == ElementKind.CONSTRUCTOR) {
            // TODO: Explicit "this()" calls of constructors have an implicit passed
            // from the enclosing constructor. We must not use the self type, but
            // instead should find a way to determine the receiver of the enclosing constructor.
            // rcv = ((AnnotatedExecutableType)atypeFactory.getAnnotatedType(atypeFactory.getEnclosingMethod(node))).getReceiverType();
            return;
        }

        // TODO: HACK MODE
        if (InferenceMain.isHackMode()) {
            if (method == null || method.getReceiverType() == null) {
                return;
            }
        }

        AnnotatedTypeMirror methodReceiver = method.getReceiverType().getErased();
        AnnotatedTypeMirror treeReceiver = methodReceiver.getCopy(false);
        AnnotatedTypeMirror rcv = atypeFactory.getReceiverType(node);
        treeReceiver.addAnnotations(rcv.getEffectiveAnnotations());

        if (!atypeFactory.getTypeHierarchy().isSubtype(treeReceiver, methodReceiver)) {
            checker.report(Result.failure("method.invocation.invalid",
                TreeUtils.elementFromUse(node),
                treeReceiver.toString(), methodReceiver.toString()), node);
        }
    }

    protected void checkConstructorInvocation(AnnotatedDeclaredType dt,
            AnnotatedExecutableType constructor, Tree src) {

        AnnotatedDeclaredType receiver = constructor.getReceiverType();
        // Only constructors for nested classes have a receiver
        if (receiver != null) {
            areComparable(dt, receiver, "constructor.invocation.invalid", src);
        }
    }

    /**
     * A helper method to check that each passed argument is a subtype of the
     * corresponding required argument, and issues "argument.invalid" error
     * for each passed argument that not a subtype of the required one.
     *
     * Note this method requires the lists to have the same length, as it
     * does not handle cases like var args.
     *
     * @param requiredArgs  the required types
     * @param passedArgs    the expressions passed to the corresponding types
     */
    protected void checkArguments(List<? extends AnnotatedTypeMirror> requiredArgs,
            List<? extends ExpressionTree> passedArgs) {
        assert requiredArgs.size() == passedArgs.size() : "mismatch between required args (" + requiredArgs +
                ") and passed args (" + passedArgs + ")";

        Pair<Tree, AnnotatedTypeMirror> preAssCtxt = visitorState.getAssignmentContext();
        try {
            for (int i = 0; i < requiredArgs.size(); ++i) {
                visitorState.setAssignmentContext(Pair.<Tree, AnnotatedTypeMirror>of((Tree) null, (AnnotatedTypeMirror) requiredArgs.get(i)));
                commonAssignmentCheck(requiredArgs.get(i), passedArgs.get(i),
                        "argument.type.incompatible", false);
                // Also descend into the argument within the correct assignment
                // context.
                scan(passedArgs.get(i), null);
            }
        } finally {
            visitorState.setAssignmentContext(preAssCtxt);
        }
    }

    /**
     * Checks that an overriding method's return type, parameter types, and
     * receiver type are correct with respect to the annotations on the
     * overridden method's return type, parameter types, and receiver type.
     *
     * <p>
     * Furthermore, any contracts on the method must satisfy behavioral
     * subtyping, that is, postconditions must be at least as strong as the
     * postcondition on the superclass, and preconditions must be at most as
     * strong as the condition on the superclass.
     *
     * <p>
     * This method returns the result of the check, but also emits error
     * messages as a side effect.
     *
     * @param overriderTree
     *            the AST node of the overriding method
     * @param enclosingType
     *            the declared type enclosing the overrider method
     * @param overridden
     *            the type of the overridden method
     * @param overriddenType
     *            the declared type enclosing the overridden method
     * @param p
     *            an optional parameter (as supplied to visitor methods)
     * @return true if the override check passed, false otherwise
     */
    protected boolean checkOverride(MethodTree overriderTree,
            AnnotatedDeclaredType enclosingType,
            AnnotatedExecutableType overridden,
            AnnotatedDeclaredType overriddenType,
            Void p) {

        if (checker.shouldSkipUses(overriddenType.getUnderlyingType().asElement())) {
            return true;
        }

        // Get the type of the overriding method.
        AnnotatedExecutableType overrider =
            atypeFactory.getAnnotatedType(overriderTree);

        boolean result = true;

        if (overrider.getTypeVariables().isEmpty()
                && !overridden.getTypeVariables().isEmpty()) {
            overridden = overridden.getErased();
        }
        String overriderMeth = overrider.toString();
        String overriderTyp = enclosingType.getUnderlyingType().asElement().toString();
        String overriddenMeth = overridden.toString();
        String overriddenTyp = overriddenType.getUnderlyingType().asElement().toString();

        // Check the return value.
        if ((overrider.getReturnType().getKind() != TypeKind.VOID)) {
            boolean success = atypeFactory.getTypeHierarchy().isSubtype(overrider.getReturnType(),
                    overridden.getReturnType());
            if (checker.hasOption("showchecks")) {
                long valuePos = positions.getStartPosition(root, overriderTree.getReturnType());
                System.out.printf(
                        " %s (line %3d):%n     overrider: %s %s (return type %s)%n   overridden: %s %s (return type %s)%n",
                        (success ? "success: overriding return type is subtype of overridden" : "FAILURE: overriding return type is not subtype of overridden"),
                        (root.getLineMap() != null ? root.getLineMap().getLineNumber(valuePos) : -1),
                        overriderMeth, overriderTyp, overrider.getReturnType().toString(),
                        overriddenMeth, overriddenTyp, overridden.getReturnType().toString());
            }
            if (!success) {
                checker.report(Result.failure("override.return.invalid",
                        overriderMeth, overriderTyp,
                        overriddenMeth, overriddenTyp,
                        overrider.getReturnType().toString(),
                        overridden.getReturnType().toString()),
                        overriderTree.getReturnType());
                // emit error message
                result = false;
            }
        }

        // Check parameter values. (FIXME varargs)
        List<AnnotatedTypeMirror> overriderParams =
            overrider.getParameterTypes();
        List<AnnotatedTypeMirror> overriddenParams =
            overridden.getParameterTypes();
        for (int i = 0; i < overriderParams.size(); ++i) {
            boolean success = atypeFactory.getTypeHierarchy().isSubtype(overriddenParams.get(i), overriderParams.get(i));
            if (checker.hasOption("showchecks")) {
                long valuePos = positions.getStartPosition(root, overriderTree.getParameters().get(i));
                System.out.printf(
                        " %s (line %3d):%n     overrider: %s %s (parameter %d type %s)%n   overridden: %s %s (parameter %d type %s)%n",
                        (success ? "success: overridden parameter type is subtype of overriding" : "FAILURE: overridden parameter type is not subtype of overriding"),
                        (root.getLineMap() != null ? root.getLineMap().getLineNumber(valuePos) : -1),
                        overriderMeth, overriderTyp, i, overriderParams.get(i).toString(),
                        overriddenMeth, overriddenTyp, i, overriddenParams.get(i).toString());
            }
            if (!success) {
                checker.report(Result.failure("override.param.invalid",
                        overriderMeth, overriderTyp,
                        overriddenMeth, overriddenTyp,
                        overriderParams.get(i).toString(),
                        overriddenParams.get(i).toString()),
                               overriderTree.getParameters().get(i));
                // emit error message
                result = false;
            }
        }

        // Check the receiver type.
        // isSubtype() requires its arguments to be actual subtypes with
        // respect to JLS, but overrider receiver is not a subtype of the
        // overridden receiver.  Hence copying the annotations.
        // TODO: this will need to be improved for generic receivers.
        AnnotatedTypeMirror overriddenReceiver =
            overrider.getReceiverType().getErased().getCopy(false);
        overriddenReceiver.addAnnotations(overridden.getReceiverType().getAnnotations());

        if (!atypeFactory.getTypeHierarchy().isSubtype(overriddenReceiver,
                overrider.getReceiverType().getErased())) {
            checker.report(Result.failure("override.receiver.invalid",
                    overriderMeth, overriderTyp, overriddenMeth, overriddenTyp,
                    overrider.getReceiverType(),
                    overridden.getReceiverType()),
                    overriderTree);
            result = false;
        }

        // Check postconditions
        ContractsUtils contracts = ContractsUtils.getInstance(atypeFactory);
        Set<Pair<String, String>> superPost = contracts
                .getPostconditions(overridden.getElement());
        Set<Pair<String, String>> subPost = contracts
                .getPostconditions(overrider.getElement());
        Set<Pair<Receiver, AnnotationMirror>> superPost2 = resolveContracts(superPost, overridden);
        Set<Pair<Receiver, AnnotationMirror>> subPost2 = resolveContracts(subPost, overrider);
        checkContractsSubset(overriderTyp, overriddenTyp, superPost2, subPost2, "contracts.postcondition.override.invalid");

        // Check preconditions
        Set<Pair<String, String>> superPre = contracts
                .getPreconditions(overridden.getElement());
        Set<Pair<String, String>> subPre = contracts.getPreconditions(overrider
                .getElement());
        Set<Pair<Receiver, AnnotationMirror>> superPre2 = resolveContracts(superPre, overridden);
        Set<Pair<Receiver, AnnotationMirror>> subPre2 = resolveContracts(subPre, overrider);
        checkContractsSubset(overriderTyp, overriddenTyp, subPre2, superPre2, "contracts.precondition.override.invalid");

        // Check conditional postconditions
        Set<Pair<String, Pair<Boolean, String>>> superCPost = contracts
                .getConditionalPostconditions(overridden.getElement());
        Set<Pair<String, Pair<Boolean, String>>> subCPost = contracts
                .getConditionalPostconditions(overrider.getElement());
        // consider only 'true' postconditions
        Set<Pair<String, String>> superCPostTrue = filterConditionalPostconditions(
                superCPost, true);
        Set<Pair<String, String>> subCPostTrue = filterConditionalPostconditions(
                subCPost, true);
        Set<Pair<Receiver, AnnotationMirror>> superCPostTrue2 = resolveContracts(
                superCPostTrue, overridden);
        Set<Pair<Receiver, AnnotationMirror>> subCPostTrue2 = resolveContracts(
                subCPostTrue, overrider);
        checkContractsSubset(overriderTyp, overriddenTyp, superCPostTrue2, subCPostTrue2,
                "contracts.conditional.postcondition.true.override.invalid");
        Set<Pair<String, String>> superCPostFalse = filterConditionalPostconditions(
                superCPost, false);
        Set<Pair<String, String>> subCPostFalse = filterConditionalPostconditions(
                subCPost, false);
        Set<Pair<Receiver, AnnotationMirror>> superCPostFalse2 = resolveContracts(
                superCPostFalse, overridden);
        Set<Pair<Receiver, AnnotationMirror>> subCPostFalse2 = resolveContracts(
                subCPostFalse, overrider);
        checkContractsSubset(overriderTyp, overriddenTyp, superCPostFalse2, subCPostFalse2,
                "contracts.conditional.postcondition.false.override.invalid");

        // check purity annotations
        Set<org.checkerframework.dataflow.qual.Pure.Kind> superPurity = new HashSet<org.checkerframework.dataflow.qual.Pure.Kind>(
                PurityUtils.getPurityKinds(atypeFactory,
                        overridden.getElement()));
        Set<org.checkerframework.dataflow.qual.Pure.Kind> subPurity = new HashSet<org.checkerframework.dataflow.qual.Pure.Kind>(
                PurityUtils.getPurityKinds(atypeFactory, overrider.getElement()));
        if (!subPurity.containsAll(superPurity)) {
            checker.report(Result.failure("purity.invalid.overriding",
                    overriderMeth, overriderTyp, overriddenMeth, overriddenTyp,
                    subPurity, superPurity), overriderTree);
        }

        return result;
    }

    /**
     * Filters the set of conditional postconditions to return only those with
     * {@code result=true}.
     */
    private <T, S> Set<Pair<T, S>> filterConditionalPostconditions(
            Set<Pair<T, Pair<Boolean, S>>> conditionalPostconditions, boolean b) {
        Set<Pair<T, S>> result = new HashSet<>();
        for (Pair<T, Pair<Boolean, S>> p : conditionalPostconditions) {
            if (p.second.first == b) {
                result.add(Pair.of(p.first, p.second.second));
            }
        }
        return result;
    }

    /**
     * Checks that {@code mustSubset} is a subset of {@code set} in the
     * following sense: For every expression in {@code mustSubset} there must be the
     * same expression in {@code set}, with the same (or a stronger) annotation.
     */
    private void checkContractsSubset(String subCl, String superCl, Set<Pair<Receiver, AnnotationMirror>> mustSubset,
            Set<Pair<Receiver, AnnotationMirror>> set, /*@CompilerMessageKey*/ String messageKey) {
        for (Pair<Receiver, AnnotationMirror> a : mustSubset) {
            boolean found = false;

            for (Pair<Receiver, AnnotationMirror> b : set) {
                // are we looking at a contract of the same receiver?
                if (a.first.equals(b.first)) {
                    // check subtyping relationship of annotations
                    QualifierHierarchy qualifierHierarchy = atypeFactory.getQualifierHierarchy();
                    if (qualifierHierarchy.isSubtype(a.second, b.second)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                MethodTree method = visitorState.getMethodTree();
                checker.report(Result.failure(messageKey, a.first, method
                        .getName().toString(), subCl, superCl), method);
            }
        }
    }

    /**
     * Takes a set of contracts identified by their expression and annotation
     * strings and resolves them to the correct {@link Receiver} and
     * {@link AnnotationMirror}.
     * @param method
     */
    private Set<Pair<Receiver, AnnotationMirror>> resolveContracts(
            Set<Pair<String, String>> contractSet, AnnotatedExecutableType method) {
        Set<Pair<Receiver, AnnotationMirror>> result = new HashSet<>();
        MethodTree methodTree = visitorState.getMethodTree();
        TreePath path = atypeFactory.getPath(methodTree);
        FlowExpressionContext flowExprContext = null;
        for (Pair<String, String> p : contractSet) {
            String expression = p.first;
            AnnotationMirror annotation = AnnotationUtils.fromName(
                    atypeFactory.getElementUtils(), p.second);

            // Only check if the postcondition concerns this checker
            if (!atypeFactory.isSupportedQualifier(annotation)) {
                continue;
            }
            if (flowExprContext == null) {
                flowExprContext = FlowExpressionParseUtil
                        .buildFlowExprContextForDeclaration(methodTree, method
                                .getReceiverType().getUnderlyingType(),
                                atypeFactory);
            }

            try {
                // TODO: currently, these expressions are parsed many times.
                // this could
                // be optimized to store the result the first time.
                // (same for other annotations)
                FlowExpressions.Receiver expr = FlowExpressionParseUtil.parse(
                        expression, flowExprContext, path);
                result.add(Pair.of(expr, annotation));
            } catch (FlowExpressionParseException e) {
                // errors are reported elsewhere + ignore this contract
            }
        }
        return result;
    }

    /**
     * Tests, for a re-assignment, whether the variable is assignable or not. If
     * not, it emits an assignability.invalid error.
     *
     * @param varType   the type of the variable being re-assigned
     * @param varTree   the tree used to access the variable in the assignment
     */
    protected void checkAssignability(AnnotatedTypeMirror varType, Tree varTree) {
        if (TreeUtils.isExpressionTree(varTree)) {
            AnnotatedTypeMirror rcvType = atypeFactory.getReceiverType((ExpressionTree) varTree);
            if(!isAssignable(varType, rcvType, varTree)) {
                checker.report(Result.failure("assignability.invalid",
                            InternalUtils.symbol(varTree),
                            rcvType),
                        varTree);
            }
        }
    }

    /**
     * Tests whether the variable accessed is an assignable variable or not,
     * given the current scope
     *
     * TODO: document which parameters are nullable; e.g. receiverType is null in
     * many cases, e.g. local variables.
     *
     * @param varType   the annotated variable type
     * @param variable  tree used to access the variable
     * @return  true iff variable is assignable in the current scope
     */
    protected boolean isAssignable(AnnotatedTypeMirror varType,
            AnnotatedTypeMirror receiverType, Tree variable) {
        return true;
    }

    protected MemberSelectTree enclosingMemberSelect() {
        TreePath path = this.getCurrentPath();
        assert path.getLeaf().getKind() == Tree.Kind.IDENTIFIER :
            "expected identifier, found: " + path.getLeaf();
        if (path.getParentPath().getLeaf().getKind() == Tree.Kind.MEMBER_SELECT)
            return (MemberSelectTree) path.getParentPath().getLeaf();
        else
            return null;
    }

    protected Tree enclosingStatement(Tree tree) {
        TreePath path = this.getCurrentPath();
        while (path != null && path.getLeaf() != tree)
            path = path.getParentPath();

        if (path != null)
            return path.getParentPath().getLeaf();
        else
            return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        checkAccess(node, p);
        return super.visitIdentifier(node, p);
    }

    protected void checkAccess(IdentifierTree node, Void p) {
        MemberSelectTree memberSel = enclosingMemberSelect();
        ExpressionTree tree;
        Element elem;

        if (memberSel == null) {
            tree = node;
            elem = TreeUtils.elementFromUse(node);
        } else {
            tree = memberSel;
            elem = TreeUtils.elementFromUse(memberSel);
        }

        if (elem == null || !elem.getKind().isField())
            return;

        AnnotatedTypeMirror receiver = atypeFactory.getReceiverType(tree);

        if (!isAccessAllowed(elem, receiver, tree)) {
            checker.report(Result.failure("unallowed.access", elem, receiver), node);
        }
    }

    protected boolean isAccessAllowed(Element field, AnnotatedTypeMirror receiver, ExpressionTree accessTree) {
        AnnotationMirror unused = atypeFactory.getDeclAnnotation(field, Unused.class);
        if (unused == null)
            return true;

        Name when = AnnotationUtils.getElementValueClassName(unused, "when", false);
        if (receiver.getAnnotation(when) == null)
            return true;

        Tree tree = this.enclosingStatement(accessTree);

        // assigning unused to null is OK
        return (tree != null
                && tree.getKind() == Tree.Kind.ASSIGNMENT
                && ((AssignmentTree)tree).getVariable() == accessTree
                && ((AssignmentTree)tree).getExpression().getKind() == Tree.Kind.NULL_LITERAL);
    }

    /**
     * Tests that the qualifiers present on the useType are valid qualifiers,
     * given the qualifiers on the declaration of the type, declarationType.
     *
     * <p>
     *
     * The check is shallow, as it does not descend into generic or array
     * types (i.e. only performing the validity check on the raw type or
     * outermost array dimension).  {@link BaseTypeVisitor#validateTypeOf(Tree)}
     * would call this for each type argument or array dimension separately.
     *
     * <p>
     *
     * For instance, in the IGJ type system, a {@code @Mutable} is an invalid
     * qualifier for {@link String}, as {@link String} is declared as
     * {@code @Immutable String}.
     *
     * <p>
     *
     * In most cases, {@code useType} simply needs to be a subtype of
     * {@code declarationType}, but there are exceptions.  In IGJ, a variable may be
     * declared {@code @ReadOnly String}, even though {@link String} is
     * {@code @Immutable String};  {@link ReadOnly} is not a subtype of
     * {@link Immutable}.
     *
     * @param declarationType the type of the class (TypeElement)
     * @param useType the use of the class (instance type)
     * @param tree the tree where the type is used
     * @return true if the useType is a valid use of elemType
     */
    public boolean isValidUse(AnnotatedDeclaredType declarationType,
            AnnotatedDeclaredType useType, Tree tree) {
        return atypeFactory.getTypeHierarchy().isSubtype(useType.getErased(), declarationType.getErased());
    }

    /**
     * Tests that the qualifiers present on the primitive type are valid.
     *
     * The default implementation always returns true.
     * Subclasses should override this method to limit what annotations are
     * allowed on primitive types.
     *
     * @param type the use of the primitive type
     * @param tree the tree where the type is used
     * @return true if the type is a valid use of the primitive type
     */
    public boolean isValidUse(AnnotatedPrimitiveType type, Tree tree) {
        return true;
    }

    /**
     * Tests that the qualifiers present on the array type are valid.
     * This method will be invoked for each array level independently, i.e. this
     * method only needs to check the top-level qualifiers of an array.
     *
     * The default implementation always returns true.
     * Subclasses should override this method to limit what annotations are
     * allowed on array types.
     *
     * @param type the array type use
     * @param tree the tree where the type is used
     * @return true if the type is a valid array type
     */
    public boolean isValidUse(AnnotatedArrayType type, Tree tree) {
        return true;
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

        // TODO: Reenable this for inference. Why does it currently fail?
//        typeValidator.isValid(type, tree);
        // more checks (also specific to checker, potentially)
        return true;
    }
    // This is a test to ensure that all types are valid
    protected final InferenceValidator typeValidator;

    protected InferenceValidator createTypeValidator() {
        return new InferenceValidator(checker, this, atypeFactory);
    }


    // **********************************************************************
    // Random helper methods
    // **********************************************************************

    /**
     * Tests whether the expression should not be checked because of the tree
     * referring to unannotated classes, as specified in
     * the {@code checker.skipUses} property.
     *
     * It returns true if exprTree is a method invocation or a field access
     * to a class whose qualified name matches @{link checker.skipUses}
     * expression.
     *
     * @param exprTree  any expression tree
     * @return true if checker should not test exprTree
     */
    protected final boolean shouldSkipUses(ExpressionTree exprTree) {
        // System.out.printf("shouldSkipUses: %s: %s%n", exprTree.getClass(), exprTree);

        // This special case for ConditionalExpressionTree seems wrong, so
        // I commented it out.  It will skip expressions that should be
        // checked, just because they are lexically near expressions that
        // should be skipped.  Presumably it's because conditionals do some
        // type inference, but if so, this is the wrong way to fix the
        // problem. -MDE
        // if (exprTree instanceof ConditionalExpressionTree) {
        //     ConditionalExpressionTree condTree =
        //         (ConditionalExpressionTree)exprTree;
        //     return (shouldSkipUses(condTree.getTrueExpression()) ||
        //             shouldSkipUses(condTree.getFalseExpression()));
        // }

        Element elm = InternalUtils.symbol(exprTree);
        return checker.shouldSkipUses(elm);
    }

    // **********************************************************************
    // Overriding to avoid visit part of the tree
    // **********************************************************************

    /**
     * Override Compilation Unit so we won't visit package names or imports
     */
    @Override
    public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
        Void r = scan(node.getPackageAnnotations(), p);
        // r = reduce(scan(node.getPackageName(), p), r);
        // r = reduce(scan(node.getImports(), p), r);
        r = reduce(scan(node.getTypeDecls(), p), r);
        return r;
    }

    // **********************************************************************
    // Check that the annotated JDK is being used.
    // **********************************************************************

    private static boolean checkedJDK = false;

    // Not all subclasses call this -- only those that have an annotated JDK.
    /** Warn if the annotated JDK is not being used. */
    protected void checkForAnnotatedJdk() {
        if (checkedJDK) {
            return;
        }
        checkedJDK = true;
        if (checker.hasOption("nocheckjdk")) {
            return;
        }
        TypeElement objectTE = elements.getTypeElement("java.lang.Object");
        List<? extends Element> members = elements.getAllMembers(objectTE);

        for (Element member : members) {
            if (member.toString().equals("equals(java.lang.Object)")) {
                ExecutableElement m = (ExecutableElement) member;
                // The Nullness JDK serves as a proxy for all annotated
                // JDKs.  (In part because of problems with
                // IGJAnnotatedTypeFactory.postAsMemberOf that make it hard
                // to directly check for the IGJ annotated JDK.)

                // Note that we cannot use the AnnotatedTypeMirrors from the
                // Checker Framework, because those only return the annotations
                // that are used by the current checker.
                // That is, if this code is executed by something other than the
                // Nullness Checker, we would not find the annotations.
                // Therefore, we go to the Element and get all annotations on
                // the parameter.

                // TODO: doing types.typeAnnotationOf(m.getParameters().get(0).asType(), Nullable.class)
                // or types.typeAnnotationsOf(m.asType())
                // does not work any more. It should.

                boolean foundNN = false;
                for (com.sun.tools.javac.code.Attribute.TypeCompound tc :
                        ((com.sun.tools.javac.code.Symbol)m).getRawTypeAttributes()) {
                    if ( tc.position.type == com.sun.tools.javac.code.TargetType.METHOD_FORMAL_PARAMETER &&
                            tc.position.parameter_index == 0 &&
                            tc.type.toString().equals(org.checkerframework.checker.nullness.qual.Nullable.class.getName()) ) {
                        foundNN = true;
                    }
                }

                if (!foundNN) {
                    String jdkJarName = PluginUtil.getJdkJarName();

                    checker.getProcessingEnvironment().getMessager().printMessage(Kind.WARNING,
                        "You do not seem to be using the distributed annotated JDK.  To fix the" +
                        System.getProperty("line.separator") +
                        "problem, supply this argument (first, fill in the \"...\") when you run javac:" +
                        System.getProperty("line.separator") +
                        "  -Xbootclasspath/p:.../checkers/binary/" + jdkJarName);
                }
            }
        }
    }
}
