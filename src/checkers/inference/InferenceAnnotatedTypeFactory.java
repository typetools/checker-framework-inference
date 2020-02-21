package checkers.inference;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.DefaultInferredTypesApplier;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.TypeHierarchy;
import org.checkerframework.framework.type.TypeVariableSubstitutor;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.LiteralTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.framework.util.dependenttypes.DependentTypesHelper;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeVariable;

import checkers.inference.dataflow.InferenceAnalysis;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.VariableSlot;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.ConstantToVariableAnnotator;
import checkers.inference.util.InferenceUtil;
import checkers.inference.util.InferenceViewpointAdapter;
import checkers.inference.util.defaults.InferenceQualifierDefaults;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;

/**
 * InferenceAnnotatedTypeFactory is responsible for creating AnnotatedTypeMirrors that are annotated with
 * "variable" or "real" annotations.  Variable annotations, represented by checkers.inference.@VarAnnot, indicate
 * an annotation with a value to be inferred.  Real annotations, those found in the hierarchy of the type system
 * for which we are inferring values, indicate that the given type has a constant value in the "real" type system.
 *
 * Adding annotations is accomplished through three means:
 * 1.  If we have the source code for a particular type, the InferenceTreeAnnotator and the VariableAnnotator
 * will add the relevant annotation (either VarAnnot or constant real annotation) based on the type's corresponding
 * tree and the rules of the InferrableChecker.  If the InferrableChecker determines that a value is constant
 * then the realAnnotatedTypeFactory is consulted to get this value.
 * @see checkers.inference.InferenceAnnotatedTypeFactory#annotateImplicit(com.sun.source.tree.Tree, checkers.types.AnnotatedTypeMirror)
 *
 * 2.  If we do NOT have the source code then the realAnnotatedTypeFactory is used to determine a constant value
 * to place on the given "library", i.e. from bytecode, type.
 * @see checkers.inference.InferenceAnnotatedTypeFactory#annotateImplicit(javax.lang.model.element.Element, checkers.types.AnnotatedTypeMirror)
 *
 * 3.  Types representing declarations generated using methods 1 and 2 are stored via
 * VariableAnnotator#storeElementType.  If these elements are encountered again, the annotations from the stored
 * type are copied to the annotations of the type being annotated.
 * @see checkers.inference.InferenceAnnotatedTypeFactory#annotateImplicit(javax.lang.model.element.Element, checkers.types.AnnotatedTypeMirror)
 *
 * Note: a number of constraints are created by members of this class
 * @see checkers.inference.InferenceQualifierHierarchy
 * @see checkers.inference.InferenceTypeHierarchy
 *
 * Finally, Variables are created while flow is being performed (every time getAnnotatedType is called on a
 * class tree that hasn't yet been annotated).  Constraints are created after flow has been for a given class when
 * the visitor requests types.
 */
public class InferenceAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    private final boolean withCombineConstraints;
    protected final VariableAnnotator variableAnnotator;
    protected final BaseAnnotatedTypeFactory realTypeFactory;

    protected final InferrableChecker realChecker;
    private final InferenceChecker inferenceChecker;
    protected final SlotManager slotManager;
    protected final ConstraintManager constraintManager;
    private final ExistentialVariableInserter existentialInserter;
    private final BytecodeTypeAnnotator bytecodeTypeAnnotator;
    private final AnnotationMirror realTop;
    private final AnnotationMirror varAnnot;
    private final InferenceQualifierPolymorphism inferencePoly;
    private final ConstantToVariableAnnotator constantToVariableAnnotator;

    public static final Logger logger = Logger.getLogger(InferenceAnnotatedTypeFactory.class.getSimpleName());

    // Used to indicate progress in the output log.  Before calling inference, if you count the number of
    // Java files you are compiling, you can use this number to gauge progress of inference.
    // See setRoot below
    public int compilationUnitsHandled = 0;

    // there are locations in the code that are constant for which we still need to apply a variable
    // though we know the value of that variable.  In this case, rather than creating a new variable
    // for every one of these locations and increase the number of variables we solve for, use
    // the same variable slot for all of these locations.  This map contains those variables.
    private Map<Class<? extends Annotation>, VariableSlot> constantToVarAnnot = new HashMap<>();

    public InferenceAnnotatedTypeFactory(
            InferenceChecker inferenceChecker,
            boolean withCombineConstraints,
            BaseAnnotatedTypeFactory realTypeFactory,
            InferrableChecker realChecker,
            SlotManager slotManager,
            ConstraintManager constraintManager) {

        super(inferenceChecker, true);

        this.withCombineConstraints = withCombineConstraints;
        this.realTypeFactory = realTypeFactory;
        this.inferenceChecker = inferenceChecker;
        this.realChecker = realChecker;
        this.slotManager = slotManager;
        this.constraintManager = constraintManager;

        variableAnnotator = createVariableAnnotator();
        bytecodeTypeAnnotator = new BytecodeTypeAnnotator(this, realTypeFactory);

        varAnnot = new AnnotationBuilder(processingEnv, VarAnnot.class).build();
        realTop = realTypeFactory.getQualifierHierarchy().getTopAnnotations().iterator().next();
        existentialInserter = new ExistentialVariableInserter(slotManager, constraintManager,
                                                              realTop, varAnnot, variableAnnotator);

        inferencePoly = new InferenceQualifierPolymorphism(slotManager, variableAnnotator, varAnnot);

        constantToVariableAnnotator = new ConstantToVariableAnnotator(realTop, varAnnot);
        // Every subclass must call postInit!
        if (this.getClass().equals(InferenceAnnotatedTypeFactory.class)) {
            this.postInit();
        }
    }

    /**
     * Get the real qualifier hierarchy from {@link #realTypeFactory}.
     * @return the real qualifier hierarchy.
     */
    public QualifierHierarchy getRealQualifierHierarchy() {
        return realTypeFactory.getQualifierHierarchy();
    }

    @Override
    protected CFAnalysis createFlowAnalysis(List<Pair<VariableElement, CFValue>> fieldValues) {
        return realChecker.createInferenceAnalysis(inferenceChecker, this, fieldValues, slotManager, constraintManager, realChecker);
    }

    @Override
    public CFTransfer createFlowTransferFunction(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return realChecker.createInferenceTransferFunction((InferenceAnalysis) analysis);
    }

    /**
     * Creates a variable annotator which adds slots and constraints. Subclasses can override this
     * method to supply a subclass of {@link VariableAnnotator} to customize the slots and
     * constraints generated.
     *
     * @return a {@link VariableAnnotator} or subclass of {@link VariableAnnotator}.
     */
    public VariableAnnotator createVariableAnnotator() {
        return new VariableAnnotator(
                this, realTypeFactory, realChecker, slotManager, constraintManager);
    }

    @Override
    protected TypeAnnotator createTypeAnnotator() {
        return new ListTypeAnnotator();
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(new LiteralTreeAnnotator(this), new InferenceTreeAnnotator(this,
                realChecker, realTypeFactory, variableAnnotator, slotManager));
    }

    @Override
    protected QualifierDefaults createQualifierDefaults() {
        // As the rawVarAnnot never make sense to be a default qualifier,
        // therefore the InferenceQualDefault should be initialized with realATF,
        // instead of inferATF.
        return new InferenceQualifierDefaults(elements, realTypeFactory);
    }

    public AnnotationMirror getVarAnnot() {
        return varAnnot;
    }

    public ConstantToVariableAnnotator getConstantToVariableAnnotator() {
        return constantToVariableAnnotator;
    }

    @Override
    protected TypeHierarchy createTypeHierarchy() {
        return new InferenceTypeHierarchy(checker, getQualifierHierarchy(), varAnnot);
    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy( MultiGraphQualifierHierarchy.MultiGraphFactory factory ) {
        return new InferenceQualifierHierarchy(factory);
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        final Set<Class<? extends Annotation>> typeQualifiers = new HashSet<>();

        typeQualifiers.add(VarAnnot.class);

        typeQualifiers.addAll(this.realTypeFactory.getSupportedTypeQualifiers());
        return typeQualifiers;
    }

    @Override
    protected void checkForDefaultQualifierInHierarchy(QualifierDefaults defs) {
        // We override this method to avoid the check for defaults
        // since in inference we don't need a default.
    }


//    @Override
//    protected TypeArgumentInference createTypeArgumentInference() {
//        return new InferenceTypeArgumentInference(slotManager, constraintManager, variableAnnotator,
//                                                  this, realTypeFactory, varAnnot);
//    }

    @Override
    protected TypeVariableSubstitutor createTypeVariableSubstitutor() {
        return new InferenceTypeVariableSubstitutor(this, existentialInserter);
    }

    @Override
    protected DependentTypesHelper createDependentTypesHelper() {
        return null;
    }

    protected Map<Class<? extends Annotation>, VariableSlot> getConstantVars() {
        return Collections.unmodifiableMap(constantToVarAnnot);
    }

    /**
     *  Copies the primary annotations on the use type "type" onto each "supertype".
     *  E.g. for a call:
     *      postDirectSuperTypes( @9 ArrayList< @7 String>, List( @4 List<@7 String>, @0 Object ) )
     *  we would like supertypes to become:
     *      List( @9 List<@7 String>, @9 Object )
     *
     * This does NOTHING to the type parameters of a declared type.  The superTypeFinder should appropriately
     * fix these up.
     */
    @Override
    protected void postDirectSuperTypes(AnnotatedTypeMirror type, List<? extends AnnotatedTypeMirror> supertypes) {

        // TODO: Move postdirectSupertypes to a "copyTypeToSuperType method" that can just be called by this method?
        // At the time of writing this is the same as AnnotatedTypeFactory.postDirectSuperTypes
        // we cannot call super.postDirectSuperTypes because GenericAnnotatedTypeFactory will cause
        // annotateImplicit(element,type) to be called on the supertype which will overwrite the annotations from type
        // with those for the declaration of the super type
        Set<AnnotationMirror> annotations = type.getEffectiveAnnotations();
        for (AnnotatedTypeMirror supertype : supertypes) {
            if (!annotations.equals(supertype.getEffectiveAnnotations())) {
                supertype.clearAnnotations();
                supertype.addAnnotations(annotations);
            }
        }
    }

    /**
     * We do not want annotations inherited from superclass, we would like to infer all positions.
     */
    @Override
    protected void addAnnotationsFromDefaultQualifierForUse(
            Element element, AnnotatedTypeMirror type)  { }


    @Override
    public void postAsMemberOf(final AnnotatedTypeMirror type,
                               final AnnotatedTypeMirror owner, final Element element) {
        if (viewpointAdapter != null) {
            viewpointAdapter.viewpointAdaptMember(owner, element, type);
        }
    }

    /**
     * @see org.checkerframework.checker.type.AnnotatedTypeFactory#methodFromUse(com.sun.source.tree.MethodInvocationTree)
     * TODO: This is essentially the default implementation of AnnotatedTypeFactory.methodFromUse with a space to later
     * TODO: add comb constraints.  One difference is how the receiver is gotten.  Perhaps we should just
     * TODO: change getSelfType?  But I am not sure where getSelfType is used yet
     * @param methodInvocationTree
     * @return
     */
    @Override
    public ParameterizedExecutableType methodFromUse(final MethodInvocationTree methodInvocationTree) {
        assert methodInvocationTree != null : "MethodInvocationTree in methodFromUse was null.  " +
                                              "Current path:\n" + this.visitorState.getPath();
        final ExecutableElement methodElem = TreeUtils.elementFromUse(methodInvocationTree);

        // TODO: Used in comb constraints, going to leave it in to ensure the element has been visited
        final AnnotatedExecutableType methodType = getAnnotatedType(methodElem);

        final ExpressionTree methodSelectExpression = methodInvocationTree.getMethodSelect();
        final AnnotatedTypeMirror receiverType;
        if (methodSelectExpression.getKind() == Tree.Kind.MEMBER_SELECT) {
            receiverType = getAnnotatedType(((MemberSelectTree) methodSelectExpression).getExpression());
        } else {
            receiverType = getSelfType(methodInvocationTree);
        }

        assert receiverType != null : "Null receiver type when getting method from use for tree ( " + methodInvocationTree + " )";

        // TODO: Add CombConstraints for method parameter types as well as return types

        // TODO: Is the asMemberOf correct, was not in Werner's original implementation but I had added it
        // TODO: It is also what the AnnotatedTypeFactory default implementation does
        final AnnotatedExecutableType methodOfReceiver = AnnotatedTypes.asMemberOf(types, this, receiverType, methodElem);
        if (viewpointAdapter != null) {
            viewpointAdapter.viewpointAdaptMethod(receiverType, methodElem, methodOfReceiver);
        }
        ParameterizedExecutableType mType = substituteTypeArgs(methodInvocationTree, methodElem, methodOfReceiver);

        AnnotatedExecutableType method = mType.executableType;
        inferencePoly.replacePolys(methodInvocationTree, method);

        if (methodInvocationTree.getKind() == Tree.Kind.METHOD_INVOCATION &&
            TreeUtils.isMethodInvocation(methodInvocationTree, objectGetClass, processingEnv)) {
            adaptGetClassReturnTypeToReceiver(method, receiverType);
        }
        return mType;
    }

    private final AnnotatedTypeScanner<Boolean, Void> fullyQualifiedVisitor = new AnnotatedTypeScanner<Boolean, Void>() {
        @Override
        public Boolean visitDeclared(AnnotatedDeclaredType type, Void p) {
            if (type.getAnnotations().size() != qualHierarchy.getWidth()) {
                return false;
            }
            return super.visitDeclared(type, p);
        }
    };

    /**
     * TODO: Similar but not the same as AnnotatedTypeFactory.constructorFromUse with space set aside from
     * TODO: comb constraints, track down the differences with constructorFromUse
     * Note: super() and this() calls
     * @see org.checkerframework.checker.type.AnnotatedTypeFactory#constructorFromUse(com.sun.source.tree.NewClassTree)
     *
     * @param newClassTree
     * @return
     */
    @Override
    public ParameterizedExecutableType constructorFromUse(final NewClassTree newClassTree) {
        assert newClassTree != null : "NewClassTree was null when attempting to get constructorFromUse. " +
                                      "Current path:\n" + this.visitorState.getPath();

        final ExecutableElement constructorElem = TreeUtils.constructor(newClassTree);;
        final AnnotatedTypeMirror constructorReturnType = fromNewClass(newClassTree);
        addComputedTypeAnnotations(newClassTree, constructorReturnType);

        final AnnotatedExecutableType constructorType = AnnotatedTypes.asMemberOf(types, this, constructorReturnType, constructorElem);

        if (viewpointAdapter != null) {
            viewpointAdapter.viewpointAdaptConstructor(constructorReturnType, constructorElem, constructorType);
        }

        ParameterizedExecutableType substitutedPair = substituteTypeArgs(newClassTree, constructorElem, constructorType);
        inferencePoly.replacePolys(newClassTree, substitutedPair.executableType);

        // TODO: Should we be doing asMemberOf like super?
        return substitutedPair;
    }

    /**
     * Get a map of the type arguments for any type variables in tree.  Create a list of type arguments by
     * replacing each type parameter of exeEle by it's corresponding argument in the map.  Substitute the
     * type parameters in exeType with those in the type arg map.
     *
     * @param expressionTree Tree representing the method or constructor we are analyzing
     * @param methodElement The element corresponding with tree
     * @param methodType The type as determined by this class of exeEle
     * @return A list of the actual type arguments for the type parameters of exeEle and exeType with it's type
     *         parameters replaced by the actual type arguments
     */
    private <EXP_TREE extends ExpressionTree> ParameterizedExecutableType substituteTypeArgs(
            EXP_TREE expressionTree, final ExecutableElement methodElement, final AnnotatedExecutableType methodType) {

        // determine substitution for method type variables
        final Map<TypeVariable, AnnotatedTypeMirror> typeVarMapping =
                AnnotatedTypes.findTypeArguments(processingEnv, this.realTypeFactory, expressionTree, methodElement, methodType);

        if (typeVarMapping.isEmpty()) {
            return new ParameterizedExecutableType(methodType, new LinkedList<AnnotatedTypeMirror>());
        } // else

        // We take the type variables from the method element, not from the annotated method.
        // For some reason, this way works, the other one doesn't.  // TODO: IS THAT TRUE?
        final List<TypeVariable> foundTypeVars   = new LinkedList<>();
        final List<TypeVariable> missingTypeVars = new LinkedList<>();

        for ( final TypeParameterElement typeParamElem : methodElement.getTypeParameters() ) {
            final TypeVariable typeParam = (TypeVariable)typeParamElem.asType();
            if (typeVarMapping.containsKey(typeParam)) {
                foundTypeVars.add(typeParam);
            } else {
                missingTypeVars.add(typeParam);
            }
        }

        if (!missingTypeVars.isEmpty()) {
            throw new BugInCF(
                "InferenceAnnotatedTypeFactory.methodFromUse did not find a mapping for " +
                "the following type params:\n" + InferenceUtil.join(missingTypeVars, "\n") +
                "in the inferred type arguments: " + InferenceUtil.join(typeVarMapping)
            );
        }

        final List<AnnotatedTypeMirror> actualTypeArgs = new ArrayList<>(foundTypeVars.size());
        for (final TypeVariable found : foundTypeVars) {
            actualTypeArgs.add(typeVarMapping.get(found));
        }

        final AnnotatedExecutableType actualExeType = (AnnotatedExecutableType)typeVarSubstitutor.substitute(typeVarMapping, methodType);

        return new ParameterizedExecutableType(actualExeType, actualTypeArgs);
    }


    @Override
    protected void performFlowAnalysis(final ClassTree classTree) {
        final InferenceMain inferenceMain = InferenceMain.getInstance();
        inferenceMain.setPerformingFlow(true);
        super.performFlowAnalysis(classTree);
        inferenceMain.setPerformingFlow(false);
    }

    /**
     * We override this to remove the extra isSubtype check when applying inferred annotations.
     * (so we don't get those extra isSubtype constraints).
     */
    @Override
    protected void applyInferredAnnotations(org.checkerframework.framework.type.AnnotatedTypeMirror type, CFValue as) {
        // TODO JB: Is this behavior different from what occured in inference?
        boolean skipSubtypingCheck = true;
        DefaultInferredTypesApplier applier =
                new DefaultInferredTypesApplier(skipSubtypingCheck,getQualifierHierarchy(), this);
        applier.applyInferredType(type, as.getAnnotations(), as.getUnderlyingType());
    }

    /**
     * This is the same as the super method, but we do not want to use defaults or typeAnnotator.
     */
    @Override
    protected void addComputedTypeAnnotations(Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
        assert root != null : "GenericAnnotatedTypeFactory.annotateImplicit: " +
                " root needs to be set when used on trees; factory: " + this.getClass();

        // Moving this here forces the type variables to be annotated as a declaration
        // before they are used and therefore ensures that they have annotations before use
        treeAnnotator.visit(tree, type);

        // TODO: instead of removing these calls, add special do-nothing type annotators/defaults.
        // typeAnnotator.visit(type, null);
         defaults.annotate(tree, type);

        if (iUseFlow) {
            CFValue as = getInferredValueFor(tree);
            if (as != null) {
                applyInferredAnnotations(type, as);
            }
        }
    }

    @Override
    public AnnotatedDeclaredType getBoxedType(AnnotatedPrimitiveType type) {
        AnnotatedDeclaredType boxedType = super.getBoxedType(type);
        for (AnnotatedTypeMirror supertype : boxedType.directSuperTypes()) {
            supertype.replaceAnnotations(type.getAnnotations());
        }

        return boxedType;
    }

    // TODO: I don't think this method is needed, but we should verify this.
//    @Override
//    public AnnotatedTypeMirror getAnnotatedTypeFromTypeTree(final Tree tree) {
//
//        if (inferenceChecker.extImplsTreeCache.contains(tree)) {
//            inferenceChecker.extImplsTreeCache(tree)
//
//        } else {
//            super.getAnnotatedTypeFromTypeTree(tree)
//
//        }
//    }

    /**
     * TODO: Expand
     * If we have a cached AnnotatedTypeMirror for the element then copy its annotations to type
     * else if we can get the source tree for the declaration of that element visit it with the tree annotator
     * else get the AnnotatedTypeMirror from the real AnnotatedTypeFactory and copy its annotations to type
     * @param element The element to annotate
     * @param type The AnnotatedTypeMirror corresponding to element
     * */
    @Override
    public void addComputedTypeAnnotations(final Element element, final AnnotatedTypeMirror type) {
        if (!variableAnnotator.annotateElementFromStore(element, type)) {

            Tree declaration = declarationFromElement(element);
            if (declaration == null) {
                // TODO: Why is the tree in the cache null
                boolean prev = this.shouldCache;
                this.shouldCache = false;
                declaration = declarationFromElement(element);
                this.shouldCache = prev;
            }

            if (declaration != null) {
                treeAnnotator.visit(declaration, type);
            } else {
                bytecodeTypeAnnotator.annotate(element, type);

            }
        }
    }

    @Override
    public void setRoot(final CompilationUnitTree root) {
        logger.fine("\nCHANGING COMPILATION UNIT ( " + compilationUnitsHandled + " ): " + root.getSourceFile().getName() + " \n");
        // TODO: THERE MAY BE STORES WE WANT TO CLEAR, PERHAPS ELEMENTS FOR LOCAL VARIABLES
        // TODO: IN THE PREVIOUS COMPILATION UNIT IN VARIABLE ANNOTATOR

        compilationUnitsHandled += 1;
        this.realTypeFactory.setRoot( root );
        this.variableAnnotator.clearTreeInfo();
        super.setRoot(root);
    }

    @Override
    protected InferenceViewpointAdapter createViewpointAdapter() {
        return withCombineConstraints ? new InferenceViewpointAdapter(this) : null;
    }

    /**
     * Get the real top annotation from {@link #realTypeFactory}.
     * @return the real top annotation.
     */
    @Override
    protected Set<? extends AnnotationMirror> getDefaultTypeDeclarationBounds() {
        return realTypeFactory.getQualifierHierarchy().getTopAnnotations();
    }
}

