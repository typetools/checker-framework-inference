package checkers.inference;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedIntersectionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedUnionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree;

import scenelib.annotations.io.ASTIndex;
import scenelib.annotations.io.ASTPath;
import scenelib.annotations.io.ASTRecord;
import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.AnnotationLocation.AstPathLocation;
import checkers.inference.model.AnnotationLocation.ClassDeclLocation;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.tree.ArtificialExtendsBoundTree;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.ASTPathUtil;
import checkers.inference.util.CopyUtil;
import checkers.inference.util.InferenceUtil;


/**
 *  VariableAnnotator takes a type and the tree that the type represents.  It determines what locations on the tree
 *  should contain a slot (i.e. locations over which we are doing inference).  For each one of these locations
 *  it:
 *     1. Checks to see if that tree has been given a VariableSlot previously, if so skip to 3
 *     2. Creates the appropriate VariableSlot or ConstantSlot for the location
 *     3. Adds an annotation representing that slot to the AnnotatedTypeMirror that corresponds to the given tree
 *     4. Stores a mapping of tree -> VariableSlot for the given tree if it contains a VariableSlot
 */
public class VariableAnnotator extends AnnotatedTypeScanner<Void,Tree> {

    private static final Logger logger = Logger.getLogger(VariableAnnotator.class.getName());

    protected final InferenceAnnotatedTypeFactory inferenceTypeFactory;
    protected final SlotManager slotManager;

    // Variable Annotator needs this to create equality constraints for pre-annotated and
    // implicit code
    protected final ConstraintManager constraintManager;

    /**
     * Store the corresponding variable slot and annotation mirrors for each
     * tree. The second parameter of pair is needed because sometimes the
     * annotation mirror for a tree is calculated (i.e least upper bound for
     * binary tree), and the calculated result is cached in the set.
     **/
    protected final Map<Tree, Pair<VariableSlot, Set<? extends AnnotationMirror>>> treeToVarAnnoPair;

    /** Store elements that have already been annotated **/
    private final Map<Element, AnnotatedTypeMirror> elementToAtm;

    private final Map<Pair<Integer,Integer>, ExistentialVariableSlot> idsToExistentialSlots;

    private final AnnotatedTypeFactory realTypeFactory;
    private final InferrableChecker realChecker;

    // Keep a different store for each of the types of missing trees
    // that we may need to find.
    // The key is the most specific identifiable object.
    /** Element is that class Element that we are storing. */
    private final Map<Element, VariableSlot> extendsMissingTrees;
    /** Element is the method for the implicit receiver we are storing. */
    private final Map<Element, AnnotatedTypeMirror> receiverMissingTrees;
    /** Key is the NewArray Tree */
    private final Map<Tree, AnnotatedArrayType> newArrayMissingTrees;
    /** Class declarations may (or may not) have annotations that act as bound. */
    private final Map<Element, VariableSlot> classDeclAnnos;

    /** When inferring the type of polymorphic qualifiers we create one new Variable to
     * represent the call-site value of that qualifier.  This map keeps track of
     * methodCall -> variable created to represent Poly qualifiers
     * See InferenceQualifierPolymorphism.
     */
    private final Map<Tree, VariableSlot> treeToPolyVar;

    // An instance of @VarAnnot
    private final AnnotationMirror varAnnot;

    // A single top in the target type system
    private final AnnotationMirror realTop;

    private final ExistentialVariableInserter existentialInserter;
    private final ImpliedTypeAnnotator impliedTypeAnnotator;

    public VariableAnnotator(final InferenceAnnotatedTypeFactory typeFactory,
                              final AnnotatedTypeFactory realTypeFactory,
                              final InferrableChecker realChecker,
                              final SlotManager slotManager, ConstraintManager constraintManager) {
        this.realTypeFactory = realTypeFactory;
        this.inferenceTypeFactory = typeFactory;
        this.slotManager = slotManager;
        this.treeToVarAnnoPair = new HashMap<>();
        this.elementToAtm   = new HashMap<>();
        this.extendsMissingTrees = new HashMap<>();
        this.receiverMissingTrees = new HashMap<>();
        this.newArrayMissingTrees = new HashMap<>();
        this.treeToPolyVar = new HashMap<>();
        this.idsToExistentialSlots = new HashMap<>();
        this.classDeclAnnos = new HashMap<>();
        this.realChecker = realChecker;
        this.constraintManager = constraintManager;
        this.varAnnot = new AnnotationBuilder(typeFactory.getProcessingEnv(), VarAnnot.class).build();
        this.realTop = realTypeFactory.getQualifierHierarchy().getTopAnnotations().iterator().next();

        this.existentialInserter = new ExistentialVariableInserter(slotManager, constraintManager, this.realTop,
                                                                   varAnnot, this);

        this.impliedTypeAnnotator = new ImpliedTypeAnnotator(inferenceTypeFactory, slotManager, existentialInserter);
    }


    public static AnnotationLocation treeToLocation(AnnotatedTypeFactory typeFactory, Tree tree) {
        final TreePath path = typeFactory.getPath(tree);

        if (path == null) {
            return AnnotationLocation.MISSING_LOCATION;
        } // else

        ASTPathUtil.getASTRecordForPath(typeFactory, path);
        if (tree.getKind() == Kind.CLASS || tree.getKind() == Kind.INTERFACE
         || tree.getKind() == Kind.ENUM  || tree.getKind() == Kind.ANNOTATION_TYPE) {
            TypeElement typeElement = TreeUtils.elementFromDeclaration((ClassTree) tree);
            return new ClassDeclLocation(((ClassSymbol)typeElement).flatName().toString());
        } // else

        ASTRecord record = ASTPathUtil.getASTRecordForPath(typeFactory, path);
        if (record == null) {
            return AnnotationLocation.MISSING_LOCATION;
        }

        return new AstPathLocation(record);

    }

    protected AnnotationLocation treeToLocation(Tree tree) {
        return treeToLocation(inferenceTypeFactory, tree);
    }

    /**
     * For each method call that uses a method with a polymorphic qualifier, we replace all uses of that polymorphic
     * qualifier with a Variable.  Sometimes we might have to later retrieve that qualifier for a given invocation
     * tree.  This method will return a previously created variable for a given invocation tree OR create a new
     * one and return it, if we haven't created one for the given tree. see InferenceQualifierPolymorphism
     * @return The Variable representing PolymorphicQualifier for the given tree
     */
    public VariableSlot getOrCreatePolyVar(Tree tree) {
        VariableSlot polyVar = treeToPolyVar.get(tree);
        if (polyVar == null) {
            polyVar = slotManager.createVariableSlot(treeToLocation(tree));
            treeToPolyVar.put(tree, polyVar);
        }

        return polyVar;
    }

    /**
     * Creates a variable for the given tree, adds it to the slotManager, and returns it.  The
     * only variables not created by this method should be those that are attached to an "implied tree",
     * (e.g. the "extends Object" that is implied in the declaration class MyClass {}).  In those
     * cases the ASTPath should be created in the calling method and the createVariable(ASTPath astPat)
     * method should be used.
     *
     * @param tree The tree to create a variable for.  Tree will be converted to an ASTPath that will
     *             be passed to the created variable
     * @return A new VariableSlot corresponding to tree
     */
    private VariableSlot createVariable(final Tree tree) {
        final VariableSlot varSlot = createVariable(treeToLocation(tree));

//        if (path != null) {
//            Element element = inferenceTypeFactory.getTreeUtils().getElement(path);
//            if ( (!element.getKind().isClass() && element.getKind().isInterface() && element.getKind().isField())) {
//
//            }
//        }

        final Pair<VariableSlot, Set<? extends AnnotationMirror>> varATMPair = Pair
                .<VariableSlot, Set<? extends AnnotationMirror>> of(varSlot,
                AnnotationUtils.createAnnotationSet());
        treeToVarAnnoPair.put(tree, varATMPair);
        logger.fine("Created variable for tree:\n" + varSlot.getId() + " => " + tree);
        return varSlot;
    }

    /**
     * Creates a variable with the given ASTPath, adds it to the slotManager, and returns it.  This method
     * should be used ONLY for "implied trees".  That is, locations that don't exist in the source code
     * but are implied by other trees.  The created variable is also added to the SlotManager
     * (e.g. the "extends Object" bound that is implied by <T> in the declaration class MyClass<T> extends List<T>{}).
     *
     * @param location The path to the "missing tree". That is, the path to the parent tree with the path to the
     *                actual implied tree appended to it.
     * @return A new VariableSlot corresponding to tree
     */
    private VariableSlot createVariable(final AnnotationLocation location) {
        final VariableSlot variableSlot = slotManager
                .createVariableSlot(location);
        return variableSlot;
    }

    public ConstantSlot createConstant(final AnnotationMirror value, final Tree tree) {
        final ConstantSlot constantSlot = slotManager.createConstantSlot(value);

//        if (path != null) {
//            Element element = inferenceTypeFactory.getTreeUtils().getElement(path);
//            if ( (!element.getKind().isClass() && element.getKind().isInterface() && element.getKind().isField())) {
//
//            }
//        }
        Set<AnnotationMirror> annotations = AnnotationUtils.createAnnotationSet();
        annotations.add(constantSlot.getValue());
        final Pair<VariableSlot, Set<? extends AnnotationMirror>> varATMPair = Pair
                .<VariableSlot, Set<? extends AnnotationMirror>> of((VariableSlot) constantSlot,
                        annotations);
        treeToVarAnnoPair.put(tree, varATMPair);
        logger.fine("Created variable for tree:\n" + constantSlot.getId() + " => " + tree);
        return constantSlot;
    }

    /**
     * ExistentialVariableSlot are used when a constraint should appear in an ExistentialConstraint.
     * Between two variable slots (a potential and alternative) we only ever need to create
     * 1 existential variable slot which we can then reuse.
     *
     * If one does not already exist, this method creates an existential variable slot between
     * potentialVariable and alternative and stores it.
     * Otherwise, it returns the previously stored ExistentialVariableSlot
     *
     * This method then applies the existential variable as a primary annotation on atm
     */
    ExistentialVariableSlot getOrCreateExistentialVariable(final AnnotatedTypeMirror atm,
                                                           final VariableSlot potentialVariable,
                                                           final VariableSlot alternativeSlot) {
        ExistentialVariableSlot existentialVariable = getOrCreateExistentialVariable(potentialVariable, alternativeSlot);
        atm.replaceAnnotation(slotManager.getAnnotation(existentialVariable));
        return existentialVariable;
    }

    /**
     * ExistentialVariableSlot are used when a constraint should appear in an ExistentialConstraint.
     * Between two variable slots (a potential and alternative) we only ever need to create
     * 1 existential variable slot which we can then reuse.
     *
     * If one does not already exist, this method creates an existential variable slot between
     * potentialVariable and alternative and stores it.
     * Otherwise, it returns the previously stored ExistentialVariableSlot
     */
    ExistentialVariableSlot getOrCreateExistentialVariable(final VariableSlot potentialVariable,
                                                           final VariableSlot alternativeSlot) {
        final Pair<Integer, Integer> idPair = Pair.of(potentialVariable.getId(), alternativeSlot.getId());
        ExistentialVariableSlot existentialVariable = idsToExistentialSlots.get(idPair);

        if (existentialVariable == null) {
            existentialVariable = slotManager.createExistentialVariableSlot(potentialVariable, alternativeSlot);
            idsToExistentialSlots.put(idPair, existentialVariable);
        } // else

        return existentialVariable;
    }

    /**
     * When getting trees to annotate types we sometimes need them from other compilation units (this occurs
     * in calls to directSupertypes for instance).  TypeFactory.getPath will NOT find these trees
     * instead use getPath.  Note, this call should not be made often since we do not cache any of the tree
     * lookups and we re-traverse from the root everytime.
     */
    public static TreePath expensiveBackupGetPath(final Element element, final Tree tree, final InferenceAnnotatedTypeFactory inferenceTypeFactory) {
        TypeElement typeElement = ElementUtils.enclosingClass(element);
        CompilationUnitTree compilationUnitTree = inferenceTypeFactory.getTreeUtils().getPath(typeElement).getCompilationUnit();
        return inferenceTypeFactory.getTreeUtils().getPath(compilationUnitTree, tree);
    }

    /**
     * Adds existential variables to a USE of a type parameter.
     * Note: See ExistentialVariableSlot for a key to the shorthand used below.
     *
     * E.g. if we have a type parameter: {@code
     *     <@0 T extends @1 Object>
     * }
     * And we have a use of T: {@code
     *     T t;
     * }
     * Then the type of t should be: {@code
     *     <(@2 (@3 | @0)) T extends (@4 (@3 | @1)) Object
     * }
     *
     * @param typeVar  A use of a type parameter
     * @param tree The tree corresponding to the use of the type parameter
     */
    private void addExistentialVariable(final AnnotatedTypeVariable typeVar, final Tree tree, boolean isUpperBoundOfTypeParam) {

        // TODO: THINK THROUGH POLY QUALS
        // Leave polymorphic qualifiers on the type. They will be replaced during methodFromUse/constructorFromUse.
//        if (typeVar.getAnnotations().size() > 0) {
//            for (AnnotationMirror aa : typeVar.getAnnotations().iterator().next().getAnnotationType().asElement().getAnnotationMirrors()) {
//                if (aa.getAnnotationType().toString().equals(PolymorphicQualifier.class.getCanonicalName())) {
//                    return;
//                }
//            }
//        }

        final VariableSlot potentialVariable;
        final Element varElem;

        final Tree typeTree;
        if (tree.getKind() == Kind.VARIABLE) {
            varElem = TreeUtils.elementFromDeclaration((VariableTree) tree);
            typeTree = ((VariableTree) tree).getType();
        } else {
            varElem = TreeUtils.elementFromUse((ExpressionTree) tree);
            typeTree = tree;
        }

        final boolean isReturn;
        if (tree.getKind() == Kind.IDENTIFIER) {
            // so this can happen when we call direct supertypes on a type for which we have the class's source
            // file.  The tree here is a type variable in that file and therefore cannot be found from
            // the root of the current compilation unit via the type factory.

            TreePath pathToTree = inferenceTypeFactory.getPath(tree);

            if (pathToTree == null) {
                pathToTree = expensiveBackupGetPath(varElem, tree, inferenceTypeFactory).getParentPath();

                if (pathToTree == null) {
                    throw new BugInCF("Could not find path to tree: " + tree + "\n"
                                           + "typeVar=" + typeVar + "\n"
                                           + "tree=" + tree + "\n"
                                           + "isUpperBoundOfTypeParam=" + isUpperBoundOfTypeParam);
                }
            }

            // TODO: What if parent is ANNOTATED_TYPE
            Tree parent = pathToTree.getParentPath().getLeaf();
            isUpperBoundOfTypeParam |= isInUpperBound(pathToTree);

            if (parent.getKind() == Tree.Kind.METHOD) {
                isReturn = true;
            } else {
                isReturn = false;
            }
        } else {
            isReturn = false;
        }
        // TODO: I think this was to guard against declarations getting here but I think
        // TODO: we might want to remove this check and just always add them (because declarations shouldn't get here?)
        if (elementToAtm.containsKey(varElem)
         && !isUpperBoundOfTypeParam
         && !isReturn) {
            typeVar.clearAnnotations();
            annotateElementFromStore(varElem, typeVar);
            return;
        }

        AnnotationMirror explicitPrimary = null;
        if (treeToVarAnnoPair.containsKey(typeTree)) {
            potentialVariable = treeToVarAnnoPair.get(typeTree).first;
            typeVar.clearAnnotations();  // might have a primary annotation lingering around
                                         // (that would removed in the else clause)

        } else {
            // element from use and see if we already have this as a local var or field?
            // if(tree.getKind() == ) // TODO: GOTTA FIGURE OUT IDENTIFIER STUFF
            if (!typeVar.getAnnotations().isEmpty()) {
                if (typeVar.getAnnotations().size() > 2) {
                    throw new BugInCF("There should be only 1 or 2 primary annotation on the typevar: \n"
                                           + "typeVar=" + typeVar + "\n"
                                           + "tree=" + tree + "\n");
                }
                typeVar.clearAnnotations();
            }

            potentialVariable = createVariable(typeTree);
            final Pair<VariableSlot, Set<? extends AnnotationMirror>> varATMPair = Pair
                    .<VariableSlot, Set<? extends AnnotationMirror>> of(
                    potentialVariable, typeVar.getAnnotations());
            treeToVarAnnoPair.put(typeTree, varATMPair);

            // TODO: explicitPrimary is null at this point! Someone needs to set it.
            if (explicitPrimary != null) {
                constraintManager.addEqualityConstraint(potentialVariable, slotManager.getSlot(explicitPrimary));
            }
        }

        final Element typeVarDeclElem = typeVar.getUnderlyingType().asElement();
        final AnnotatedTypeMirror typeVarDecl;
        if (!elementToAtm.containsKey(typeVarDeclElem)) {
            // e.g. <T extends E, E extends Object>
            // TODO: THIS CRASHES ON RECURSIVE TYPES
            typeVarDecl = inferenceTypeFactory.getAnnotatedType(typeVarDeclElem);
        } else {
            typeVarDecl = elementToAtm.get(typeVarDeclElem);
            // TODO: I THINK THIS IS UNNECESSARY DUE TO InferenceVisitor.visitVariable
//            if(tree instanceof VariableTree && !treeToVariable.containsKey(tree)) { // if it's a declaration of a variable, store it
//                final Element varElement = TreeUtils.elementFromDeclaration((VariableTree) tree);
//                storeElementType(varElement, typeVar);
//                treeToVariable.put(tree, potentialVariable);
//            }
        }

        existentialInserter.insert(potentialVariable, typeVar, typeVarDecl);
    }

    // TODO JB: I think this means we don't need the isUpperBoundOfTypeParam parameter above
    private boolean isInUpperBound(TreePath path) {
        TreePath parentPath = path;

        Tree parent;
        do {
            parentPath = parentPath.getParentPath();
            parent = parentPath.getLeaf();

            if (parent.getKind() == Kind.TYPE_PARAMETER) {
                return true;
            }

        } while (parent.getKind() == Kind.PARAMETERIZED_TYPE
             ||  parent.getKind() == Kind.ANNOTATED_TYPE);

        return false;
    }

    /**
     * If treeToVariable contains tree, add the stored variable as a primary annotation to atm
     * If treeToVariable does not contain tree, create a new variable as a primary annotation to atm
     *
     * If any annotation exist on Atm already create an EqualityConstraint between that annotation and
     * the one added to atm.  The original annotation is cleared from atm
     * @param atm Annotation mirror representing tree, atm will have a VarAnnot as its primary annotation
     *            after this method completes
     * @param tree Tree for which we want to create variables
     */
    private VariableSlot addPrimaryVariable(AnnotatedTypeMirror atm, final Tree tree) {

        final VariableSlot variable;
        if (treeToVarAnnoPair.containsKey(tree)) {
            variable = treeToVarAnnoPair.get(tree).first;

            // The record will be null if we created a variable for a tree in a different compilation unit.
            // When that compilation unit is visited we will be able to get the record.
            if (variable.getLocation() == null) {
                variable.setLocation(treeToLocation(tree));
            }
        } else {
            AnnotationLocation location = treeToLocation(tree);
            variable = replaceOrCreateEquivalentVarAnno(atm, tree, location);
            final Pair<VariableSlot, Set<? extends AnnotationMirror>> varATMPair = Pair
                    .of(variable,
                    AnnotationUtils.createAnnotationSet());

            treeToVarAnnoPair.put(tree, varATMPair);
        }

        atm.replaceAnnotation(slotManager.getAnnotation(variable));

        return variable;
    }

    /**
     * If we have a tree to a type use that is implied, such as:{@code
     *     extends String
     * }
     * Create a variable for the primary annotation on the type.  For the above example,
     * the record argument would be an ASTRecord that points to the annotation @1 below: {@code
     *     extends @1 String
     * }
     *
     * We create a variable annotation for @1 and place it in the primary annotation position of
     * the type.
     */
    public VariableSlot addImpliedPrimaryVariable(AnnotatedTypeMirror atm, final AnnotationLocation location) {
        VariableSlot variable = slotManager.createVariableSlot(location);
        atm.addAnnotation(slotManager.getAnnotation(variable));

        AnnotationMirror realAnno = atm.getAnnotationInHierarchy(realTop);
        if (realAnno != null) {
            constraintManager.addEqualityConstraint(slotManager.getSlot(realAnno), variable);
        }

        logger.fine("Created implied variable for type:\n" + atm + " => " + location);

        return variable;
    }

    /**
     * Given an atm, replace its real annotation from pre-annotated code and implicit from the underlying type system
     * by the equivalent varAnnotation, or creating a new VarAnnotation for it if doesn't have any existing annotations.
     */
    private VariableSlot replaceOrCreateEquivalentVarAnno(AnnotatedTypeMirror atm, Tree tree, final AnnotationLocation location) {
        VariableSlot varSlot = null;
        AnnotationMirror realQualifier = null;

        AnnotationMirror existinVar = atm.getAnnotationInHierarchy(varAnnot);
        if (existinVar != null) {
            varSlot = slotManager.getVariableSlot(atm);
        } else if (!atm.getAnnotations().isEmpty()) {
            realQualifier = atm.getAnnotationInHierarchy(realTop);
            if (realQualifier == null) {
                throw new BugInCF("The annotation(s) on the given type is neither VarAnno nor real qualifier!"
                        + "Atm is: " + atm + " annotations: " + atm.getAnnotations());
            }
            varSlot = slotManager.createConstantSlot(realQualifier);
        } else if (tree != null && realChecker.isConstant(tree) ) {
            // Considered constant by real type system
            realQualifier = realTypeFactory.getAnnotatedType(tree).getAnnotationInHierarchy(realTop);
            varSlot = slotManager.createConstantSlot(realQualifier);
        } else {
            varSlot = createVariable(location);
        }

        atm.replaceAnnotation(slotManager.getAnnotation(varSlot));
        return varSlot;
    }

    public VariableSlot getTopConstant() {
        return slotManager.createConstantSlot(realTop);
    }

    /**
     * Stores the given AnnotatedTypeMirror with element as a key.
     * @see checkers.inference.VariableAnnotator#annotateElementFromStore
     */
    public void storeElementType(final Element element, final AnnotatedTypeMirror atm) {
        elementToAtm.put(element, atm);
    }

    /**
     * For the given tree,  create or retrieve variable or constant annotations and place
     * them on the AnnotatedDeclaredType.  Note, often AnnotatedDeclaredTypes are associated with VariableTrees
     * but they should NOT be passed as a tree here.  Instead pass their identifier.
     *
     * @param adt A type to annotate
     * @param tree A tree of kind:
     *             ANNOTATION_TYPE, CLASS, INTERFACE, ENUM, STRING_LITERAL, IDENTIFIER,
     *             ANNOTATED_TYPE, TYPE_PARAMETER, MEMBER_SELECT, PARAMETERIZED_TYPE
     * @return null
     */
    @Override
    public Void visitDeclared(final AnnotatedDeclaredType adt, final Tree tree) {

        if (tree instanceof BinaryTree) {
            // Since there are so many kinds of binary trees
            // handle these with an if instead of in the switch.
            handleBinaryTree(adt, (BinaryTree)tree);
            return null;
        }

        // TODO: For class declarations, create a map of classDecl -> ExistentialVariableAnnotator
        // TODO: and make a constraint between it

        switch (tree.getKind()) {
        case ANNOTATION_TYPE:
        case CLASS:
        case INTERFACE:
        case ENUM: // TODO: MORE TO DO HERE?
            handleClassDeclaration(adt, (ClassTree) tree);
            break;

        case ANNOTATED_TYPE: // We need to do this for Identifiers that are
                             // already annotated.
        case STRING_LITERAL:
        case IDENTIFIER:
            VariableSlot primary = addPrimaryVariable(adt, tree);
            handleWasRawDeclaredTypes(adt);
            addDeclarationConstraints(getOrCreateDeclBound(adt), primary);
            break;

        case VARIABLE:
            final Element varElement = TreeUtils.elementFromDeclaration((VariableTree) tree);
            if (varElement.getKind() == ElementKind.ENUM_CONSTANT) {
                AnnotatedTypeMirror realType = realTypeFactory.getAnnotatedType(tree);
                CopyUtil.copyAnnotations(realType, adt);
                inferenceTypeFactory.getConstantToVariableAnnotator().visit(adt);
            } else {
                // calls this method again but with a ParameterizedTypeTree
                visitDeclared(adt, ((VariableTree) tree).getType());
            }
            break;

        case TYPE_PARAMETER:
            // TODO: I assume that the only way a TypeParameterTree is going to
            // have an ADT as its
            // TODO: AnnotatedTypeMirror is through either a
            // getEffectiveAnnotation call or some other
            // TODO: call that will treat the type parameter as it's upper bound
            // but we should probably
            // TODO: inspect this in order to have an idea of when this happens

            final TypeParameterTree typeParamTree = (TypeParameterTree) tree;

            if (typeParamTree.getBounds().isEmpty()) {
                primary = addPrimaryVariable(adt, tree);
                addDeclarationConstraints(getOrCreateDeclBound(adt), primary);
                // TODO: HANDLE MISSING EXTENDS BOUND?
            } else {
                visit(adt, typeParamTree.getBounds().get(0));
            }
            break;

        case MEMBER_SELECT:
            primary = addPrimaryVariable(adt, tree);
            // We only need to dive into the expression if it is not an
            // identifier.
            // Otherwise we may try to annotate the outer class for a
            // Outer.Inner static class.
            if (adt.getEnclosingType() != null
                    && ((MemberSelectTree) tree).getExpression().getKind() != Tree.Kind.IDENTIFIER) {
                visit(adt.getEnclosingType(), ((MemberSelectTree) tree).getExpression());
            }
            addDeclarationConstraints(getOrCreateDeclBound(adt), primary);
            break;

        case PARAMETERIZED_TYPE:
            final ParameterizedTypeTree parameterizedTypeTree = (ParameterizedTypeTree) tree;
            primary = addPrimaryVariable(adt, parameterizedTypeTree.getType());
            // visit(adt, parameterizedTypeTree.getType());

            AnnotatedDeclaredType newAdt = adt;

            if (!handleWasRawDeclaredTypes(newAdt)
                    && !parameterizedTypeTree.getTypeArguments().isEmpty()) {
                if (TypesUtils.isAnonymous(newAdt.getUnderlyingType())) {
                    // There are multiple super classes for an anonymous class
                    // if the name following new keyword specifies an interface,
                    // and the anonymous class implements that interface and
                    // extends Object. In this case, we need the following for
                    // loop to find out the AnnotatedTypeMirror for the
                    // interface.
                    for (AnnotatedDeclaredType adtSuper : newAdt.directSuperTypes()) {
                        if (TreeUtils.typeOf(parameterizedTypeTree).equals(
                                adtSuper.getUnderlyingType())) {
                            newAdt = adtSuper;
                         }
                    }
                }

                final List<? extends Tree> treeArgs = parameterizedTypeTree.getTypeArguments();
                final List<AnnotatedTypeMirror> typeArgs = newAdt.getTypeArguments();

                if (treeArgs.size() != typeArgs.size()) {
                    throw new BugInCF("Raw type? Tree(" + parameterizedTypeTree + "), Atm(" + newAdt + ")");
                }

                for (int i = 0; i < typeArgs.size(); i++) {
                    final AnnotatedTypeMirror typeArg = typeArgs.get(i);
                    visit(typeArg, treeArgs.get(i));
                }
            }
            addDeclarationConstraints(getOrCreateDeclBound(newAdt), primary);
            break;

        default:
            throw new IllegalArgumentException("Unexpected tree type ( kind=" + tree.getKind() + " tree= " + tree
                    + " ) when visiting " + "AnnotatedDeclaredType( " + adt + " )");
        }

        return null;
    }

    private boolean handleWasRawDeclaredTypes(AnnotatedDeclaredType adt) {
        if (adt.wasRaw() && adt.getTypeArguments().size() != 0) {
            // the type arguments should be wildcards AND if I get the real type of "tree"
            // it corresponds to the declaration of adt.getUnderlyingType
            Element declarationEle = adt.getUnderlyingType().asElement();
            final AnnotatedDeclaredType declaration =
                    (AnnotatedDeclaredType) inferenceTypeFactory.getAnnotatedType(declarationEle);

            final List<AnnotatedTypeMirror> declarationTypeArgs = declaration.getTypeArguments();
            final List<AnnotatedTypeMirror> rawTypeArgs = adt.getTypeArguments();

            for (int i = 0; i < declarationTypeArgs.size(); i++) {
                final AnnotatedTypeVariable declArg = (AnnotatedTypeVariable) declarationTypeArgs.get(i);

                if (InferenceMain.isHackMode(rawTypeArgs.get(i).getKind() != TypeKind.WILDCARD)) {
                    return false;
                }

                final AnnotatedWildcardType rawArg = (AnnotatedWildcardType) rawTypeArgs.get(i);

                rawArg.getExtendsBound().replaceAnnotation(declArg.getUpperBound().getAnnotationInHierarchy(varAnnot));
                rawArg.getSuperBound().replaceAnnotation(declArg.getLowerBound().getAnnotationInHierarchy(varAnnot));
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Visit the extends, implements, and type parameters of the given class type and tree.
     */
    private void handleClassDeclaration(AnnotatedDeclaredType classType, ClassTree classTree) {
        final Tree extendsTree = classTree.getExtendsClause();
        if (extendsTree == null) {
            // Annotated the implicit extends.
            Element classElement = classType.getUnderlyingType().asElement();
            VariableSlot extendsSlot;
            if (!extendsMissingTrees.containsKey(classElement)) {
                // TODO: SEE COMMENT ON createImpliedExtendsLocation
                AnnotationLocation location = createImpliedExtendsLocation(classTree);
                extendsSlot = createVariable(location);
                extendsMissingTrees.put(classElement, extendsSlot);
                logger.fine("Created variable for implicit extends on class:\n" +
                        extendsSlot.getId() + " => " + classElement + " (extends Object)");

            } else {
                // Add annotation
                extendsSlot = extendsMissingTrees.get(classElement);
            }
            List<AnnotatedDeclaredType> superTypes = classType.directSuperTypes();
            superTypes.get(0).replaceAnnotation(slotManager.getAnnotation(extendsSlot));

        } else {
            final AnnotatedTypeMirror extendsType = inferenceTypeFactory.getAnnotatedTypeFromTypeTree(extendsTree);
            visit(extendsType, extendsTree);
        }

//        // TODO: NOT SURE THIS HANDLES MEMBER SELECT CORRECTLY
//        int interfaceIndex = 1;
//        for(Tree implementsTree : classTree.getImplementsClause()) {
//            final AnnotatedTypeMirror implementsType = inferenceTypeFactory.getAnnotatedTypeFromTypeTree(implementsTree);
//            AnnotatedTypeMirror supertype = classType.directSuperTypes().get(interfaceIndex);
//            assert supertype.getUnderlyingType() == implementsType.getUnderlyingType();
//            visit(supertype, implementsTree);
//            interfaceIndex++;
//        }
//
        if (InferenceMain.isHackMode(
                (classType.getTypeArguments().size() != classTree.getTypeParameters().size()))) {
            return;
        }

        visitTogether(classType.getTypeArguments(), classTree.getTypeParameters());

        VariableSlot varSlot = getOrCreateDeclBound(classType);
        classType.addAnnotation(slotManager.getAnnotation(varSlot));

        // before we were relying on trees but the ClassTree has it's type args erased
        // when the compiler moves on to the next class
        Element classElement = classType.getUnderlyingType().asElement();
        storeElementType(classElement, classType);

    }

    /**
     * I BELIEVE THIS METHOD IS NO LONGER NEEDED BECAUSE WE DON'T HAVE SEMANTICS FOR the extends LOCATION
     * ON A CLASS IN THE CHECKER FRAMEWORK.  Mike Ernst, Javier Thaine, Werner Deitl, and Suzanne Millstein have an
     * email entitled "Annotation on Class Name" that covers this.  But the gist is, Werner does not see the
     * need for an annotation on the extends bound and we currently have no semantics for it.
     *
     * Note, if we have on on the extends bound, you can also have one on every implemented interface.  Which
     * are other locations we don't have sematnics for.
     */
    private AnnotationLocation createImpliedExtendsLocation(ClassTree classTree) {
        // TODO: THIS CAN BE CREATED ONCE THIS IS FIXED: https://github.com/typetools/annotation-tools/issues/100
        InferenceMain.getInstance().logger.warning("Hack:VariableAnnotator::createImpliedExtendsLocation(classTree) not implemented");
        return AnnotationLocation.MISSING_LOCATION;
    }

    /**
     * Creates an AnnotationLocation that represents the implied (missing bound) on a type parameter
     * that extends object.  E.g. {@code <T> } the "extends Object" on T is implied but not written.
     */
    private AnnotationLocation createImpliedExtendsLocation(TypeParameterTree typeParamTree) {
        AnnotationLocation parentLoc = treeToLocation(typeParamTree);

        AnnotationLocation result;
        switch (parentLoc.getKind()) {
            case AST_PATH:
                ASTRecord parent = ((AstPathLocation) parentLoc).getAstRecord();
                result = new AstPathLocation(parent.extend(Kind.TYPE_PARAMETER, "bound", 0));
                break;

            case MISSING:
                result = AnnotationLocation.MISSING_LOCATION;
                break;

            default:
                throw new RuntimeException("Unexpected location " + parentLoc.getKind() + " location kind for tree:\n"
                                         + typeParamTree);
        }

        return result;
    }

    /**
     * Visit each bound on the intersection type
     * @param intersectionType type to annotate
     * @param tree An AnnotatedIntersectionTypeTree, an IllegalArgumentException will be thrown otherwise
     * @return null
     */
    @Override
    public Void visitIntersection(AnnotatedIntersectionType intersectionType, Tree tree) {

        if (InferenceMain.isHackMode(!(tree instanceof IntersectionTypeTree))) {
            return null;
        }

        // TODO: THERE ARE PROBABLY INSTANCES OF THIS THAT I DON'T KNOW ABOUT, CONSULT WERNER
        // TODO: AND DO GENERAL TESTING/THINKING ABOUT WHAT WE WANT TO DO WITH INTERSECTIONS

        switch (tree.getKind()) {

            case INTERSECTION_TYPE:
                assert ((IntersectionTypeTree) tree).getBounds().size() == intersectionType.directSuperTypes().size();
                visitTogether(intersectionType.directSuperTypes(), ((IntersectionTypeTree) tree).getBounds());
                break;

            case TYPE_PARAMETER:
                assert ((TypeParameterTree) tree).getBounds().size() == intersectionType.directSuperTypes().size();
                visitTogether(intersectionType.directSuperTypes(), ((TypeParameterTree) tree).getBounds());
                break;

            // TODO: IN JAVA 8, LAMBDAS CAN HAVE INTERSECTION ARGUMENTS

            default:
                InferenceUtil.testArgument(false,
                        "Unexpected tree type ( " + tree + " ) when visiting AnnotatedIntersectionType( " + intersectionType + " )");
        }

        // TODO: So in Java 8 the Ast the "A & B" tree in T extends A & B is an IntersectionTypeTree
        // TODO: but there are also casts of type (A & B) I believe
//        visitTogether(intersectionType.directSuperTypes(), ((IntersectionTypeTree) tree).getBounds());

        return null;
    }

    /**
     * Visit each alternative in the union type
     * @param unionType type to be annotated
     * @param tree must be a UnionTypeTree
     * @return null
     */
    @Override
    public Void visitUnion(final AnnotatedUnionType unionType, final Tree tree) {

        InferenceUtil.testArgument(tree instanceof UnionTypeTree || tree instanceof VariableTree,
            "Unexpected tree type ( " + tree + " ) for AnnotatedUnionType (" + unionType + ")");


        UnionTypeTree unionTree;
        if (tree instanceof VariableTree) {
            VariableTree varTree = (VariableTree) tree;
            Tree typeTree = varTree.getType();
            InferenceUtil.testArgument(typeTree instanceof UnionTypeTree,
                    "Unexpected tree type ( " + tree + " ) for variable tree of type AnnotatedUnionType (" + unionType + ")");
            unionTree = (UnionTypeTree) typeTree;
        } else {
            unionTree = (UnionTypeTree) tree;
        }

        final List<? extends Tree> unionTrees = unionTree.getTypeAlternatives();
        final List<AnnotatedDeclaredType> unionTypes = unionType.getAlternatives();
        for (int i = 0; i < unionTypes.size(); ++i) {
            visit(unionTypes.get(i), unionTrees.get(i));
        }

        return null;
    }

    /**
     * Annotates the array type of the given AnnotatedArrayType.
     *
     * @param type The type to be annotated
     * @param tree A tree of kind: ARRAY_TYPE, NEW_ARRAY, ANNOTATION_TYPE
     *             an IllegalArgumentException is thrown otherwise
     * @return null
     */
    @Override
    public Void visitArray(AnnotatedArrayType type, Tree tree) {

        // TODO: Are there other places that we need check for an AnnotatedTypeTree wrapper.
        // TODO: Apparently AnnotatedTypeTree will be going away soon (removed in javac).
        Tree effectiveTree = tree;
        // This is a while loop because variable declarations may have ANNOTATED_TYPE as their type,
        // unwrap till we get an ARRAY_TYPE
        while (effectiveTree.getKind() == Kind.ANNOTATED_TYPE || effectiveTree.getKind() == Kind.VARIABLE) {
            if (effectiveTree.getKind() == Kind.ANNOTATED_TYPE) {
                // This happens for arrays that are already annotated.
                effectiveTree = ((JCTree.JCAnnotatedType) effectiveTree).getUnderlyingType();
            } else if (effectiveTree.getKind() == Kind.VARIABLE) {
                // variable declarations may have array types
                effectiveTree = ((VariableTree) effectiveTree).getType();
            }
        }

        switch (effectiveTree.getKind()) {
            case ARRAY_TYPE:
                // ARRAY_TYPE is straightforward
                // Annotate the primary and then scan the component,
                // (which will recursively visitArray for multidimensional arrays))
                Tree componentTree = ((ArrayTypeTree) effectiveTree).getType();
                addPrimaryVariable(type, effectiveTree);
                visit(type.getComponentType(), componentTree);

                break;

            case NEW_ARRAY:
                // New array is harder
                // new Array[1][]
                // new Array[1][1]
                // new Array[]{"A", "B", "C"}
                // {"1", "2", "3"}
                // { { "1", "2", "3" }, {"X", "Y", "Z"}}

                // When dealing with AnnotatedArrayTypes for a NewArrayTree,
                // some of the annotatable positions will not have any corresponding tree
                // so we can't just use addPrimaryVariable since there is no tree associated with it.
                // Instead, we cache the entire AnnotatedArrayType and return it the next time this method
                // is called for that tree.
                if (newArrayMissingTrees.containsKey(effectiveTree)) {
                    CopyUtil.copyAnnotations(newArrayMissingTrees.get(effectiveTree), type);
                    return null;
                }

                boolean isArrayLiteral = (((NewArrayTree) effectiveTree).getType() == null);
                if (isArrayLiteral) {
                    // {"1", "2", "3"}
                    annotateArrayLiteral(type, (NewArrayTree)effectiveTree);
                } else {
                    // new Array[1][]
                    // new Array[1][1]
                    // new Array[1][] {{"", ""}}
                    //
                    // Note that new Array[1][] and new Array[1][1] have different trees
                    // so we have a special method to handle.
                    annotateNewArray(type, effectiveTree, 0, effectiveTree);
                }

                // Store result
                newArrayMissingTrees.put(effectiveTree, type);
                break;

            case ANNOTATION_TYPE:
                // TODO: Do we have a test for these.
                addPrimaryVariable(type, effectiveTree);
                break;
            default:
                throw new IllegalArgumentException("Unexpected tree (" + tree + ") for type (" + type + ")");
        }

        return null;
    }


    public boolean enclosedByAnnotation(TreePath path) {

        Set<Tree.Kind> treeKinds = new HashSet<>();
        treeKinds.add(Kind.ANNOTATION);
        treeKinds.add(Kind.ANNOTATION_TYPE);
        treeKinds.add(Kind.TYPE_ANNOTATION);
        treeKinds.add(Kind.METHOD);
        treeKinds.add(Kind.CLASS);
        Tree enclosure = TreeUtils.enclosingOfKind(path, treeKinds);
        return enclosure.getKind() == Kind.ANNOTATION
            || enclosure.getKind() == Kind.ANNOTATION_TYPE
            || enclosure.getKind() == Kind.TYPE_ANNOTATION;
    }

    /**
     * Create VariableSlots to a NewArrayTree.
     *
     * An array literal like the RHS of this
     * String[][] = {{"a", "b"}, {}, null}
     *
     * is really
     * String[][] = new @A String @B [] @C [] { new @D String @E []{"a", "b"}, new @F String @G []{}, null}
     *
     * This method adds variables for the @A,@B,@C.
     * The intializers will be annotated when their ATM is created.
     *
     * @param type the type corresponding to the array literal
     * @param tree The tree corresponding to an array literal
     */
    private void annotateArrayLiteral(AnnotatedArrayType type, NewArrayTree tree) {
        assert tree.getType() == null : "annotateArrayLiteral called on a non-literal!";

        TreePath path = inferenceTypeFactory.getPath(tree);

        if (path == null || enclosedByAnnotation(path)) {
            AnnotatedTypeMirror realType = realTypeFactory.getAnnotatedType(tree);
            CopyUtil.copyAnnotations(realType, type);
            inferenceTypeFactory.getConstantToVariableAnnotator().visit(type);
            return;
        } // else

        // add an actual variable
        // Add a variable to the outer type.
        VariableSlot slot = addPrimaryVariable(type, tree);

        TreePath pathToTree = inferenceTypeFactory.getPath(tree);
        ASTRecord astRecord = ASTPathUtil.getASTRecordForPath(inferenceTypeFactory, pathToTree);
        if (astRecord == null) {
            if (InferenceMain.isHackMode()) {
                return;
            } else {
                throw new BugInCF("NULL ARRAY RECORD:\n" + tree + "\n\n");
            }
        }
        slot.setLocation(new AstPathLocation(astRecord.newArrayLevel(0)));

        // The current type of the level we are trying to annotate
        AnnotatedTypeMirror loopType = type;
        int level = 0;
        while (loopType instanceof AnnotatedArrayType) {
            loopType = ((AnnotatedArrayType) loopType).getComponentType();
            level ++;

            ASTRecord astRec = astRecord.newArrayLevel(level);
            replaceOrCreateEquivalentVarAnno(loopType, tree, new AstPathLocation(astRec));
        }
    }

    /**
     * Recursively creates annotations for an Array.
     *
     * This needs special handling to correctly
     * number the ASTRecord entries, and because these two expressions correspond to different trees.
     * (Is this a compiler bug?)
     *
     * new Array[1][]
     * new Array[1][1]
     *
     * The latter is missing a tree for the nested string array; the component tree for new String[1][1]
     * is just String. This means there is no tree to associate the @VarAnnot type with.
     * This assigns an @VarAnnot to the missing tree, and the full result will be cached by newArrayMissingTrees.
     *
     * @param type
     * @param tree
     * @param level
     * @param topLevelTree
     */
    private void annotateNewArray(AnnotatedTypeMirror type, Tree tree, int level, Tree topLevelTree) {

        if (type instanceof AnnotatedArrayType
                && (tree.getKind() == Tree.Kind.NEW_ARRAY
                    || tree.getKind() == Tree.Kind.ARRAY_TYPE)) {
            // The tree is an array type.
            // The outer if check is needed because sometimes the tree might be a declared type.
            shallowAnnotateArray(type, tree, level, topLevelTree);

        } else if (type instanceof AnnotatedArrayType) {
            // The tree is a declared type, which happens, although is unintuitive. Might be a compiler bug.
            // The tree doesn't correspond to the type, so it is effectively missing.
            // This is one reason for having newArrayMissingTrees

            // Create a variable from an ASTPath
            final TreePath pathToTree = inferenceTypeFactory.getPath(topLevelTree);
            ASTRecord astRec = ASTPathUtil.getASTRecordForPath(inferenceTypeFactory, pathToTree).newArrayLevel(level);
            replaceOrCreateEquivalentVarAnno(type, tree, new AstPathLocation(astRec));

        } else if (!(tree.getKind() == Tree.Kind.NEW_ARRAY
                     || tree.getKind() == Tree.Kind.ARRAY_TYPE)) {

            // Annotate the declared type for the component tree.
            // The inner most component type always has a corresponding tree.
            shallowAnnotateArray(type, tree, level, topLevelTree);

        } else {

            // The inner most component type, but it has an
            // array tree. Something is wrong.
            throw new BugInCF("Annotate array is broken. Presumably there is a bug.");
        }
        if (type instanceof AnnotatedArrayType) {
            Tree componentTree;
            if (tree.getKind() == Tree.Kind.ARRAY_TYPE) {
                componentTree = ((ArrayTypeTree) tree).getType();
            } else if (tree.getKind() == Tree.Kind.NEW_ARRAY) {
                componentTree = ((NewArrayTree) tree).getType();
            } else {
                // A component with a missing tree
                componentTree = tree;
            }
            level += 1;
            annotateNewArray(((AnnotatedArrayType) type).getComponentType(), componentTree, level, topLevelTree);
        }

    }

    /**
     * Add a primary annotation to the top level of an array. Special handling is needed to create the ASTRecord
     * correctly.
     */
    private void shallowAnnotateArray(AnnotatedTypeMirror type, Tree tree, int level, Tree topLevelTree) {
        if (treeToVarAnnoPair.containsKey(tree)) {
            addPrimaryVariable(type, tree);
        } else {
            TreePath pathToTopLevelTree = inferenceTypeFactory.getPath(topLevelTree);

            AnnotationLocation location;
            // 'pathToTopLevelTree' is null when it's an artificial array creation tree like for
            // varargs. We don't need to create AstPathLocation for them.
            if (pathToTopLevelTree != null) {
                ASTRecord astRecord = ASTPathUtil.getASTRecordForPath(inferenceTypeFactory, pathToTopLevelTree).newArrayLevel(level);
                location = new AstPathLocation(astRecord);
            } else {
                location = AnnotationLocation.MISSING_LOCATION;
            }

            replaceOrCreateEquivalentVarAnno(type, tree, location);
        }
    }

    /**
     * If the given typeVar represents a declaration (TypeParameterTree), the adds annotations to the upper and
     * lower bounds of the given type variable.  If the given typeVar reperesents a typeUse, adds a primary annotation
     * to the type variable and stores the element -> typeVa
     * @param typeVar type variable to annotate
     * @param tree A tree of kind TYPE_PARAMETER leads to creation of bounds variable, other tree kinds are treated as
     *             type uses
     * @return null
     */
    @Override
    public Void visitTypeVariable(AnnotatedTypeVariable typeVar, Tree tree) {

        if (tree.getKind() == Tree.Kind.TYPE_PARAMETER) {
            final TypeParameterElement typeParamElement = (TypeParameterElement) typeVar.getUnderlyingType().asElement();
            final TypeParameterTree typeParameterTree   = (TypeParameterTree) tree;

            if (!elementToAtm.containsKey(typeParamElement)) {
                storeElementType(typeParamElement, typeVar);
            }

            // add lower bound annotation
            addPrimaryVariable(typeVar.getLowerBound(), tree);

            if (typeParameterTree.getBounds().size() > 0) {
                final AnnotatedTypeMirror upperBound = typeVar.getUpperBound();
                if (upperBound.getKind() == TypeKind.TYPEVAR) {
                    addExistentialVariable((AnnotatedTypeVariable) upperBound,
                                            typeParameterTree.getBounds().get(0), true);
                } else {

                    Tree bound = typeParameterTree.getBounds().get(0);

                    if (upperBound.getKind() == TypeKind.INTERSECTION) {
                        // sometimes all of the bounds are in the bound list and sometimes there seem to be
                        // nested intersection type trees.
                        if (bound.getKind() != Kind.INTERSECTION_TYPE) {
                            visit(upperBound, typeParameterTree);
                        } else {
                            visit(upperBound, bound);
                        }
                    } else {
                        visit(upperBound, bound);
                    }
                }
            } else {
                final TypeParameterElement typeVarElement = (TypeParameterElement) typeVar.getUnderlyingType().asElement();

                final VariableSlot extendsSlot;
                if (!extendsMissingTrees.containsKey(typeVarElement)) {
                    AnnotationLocation location = createImpliedExtendsLocation(typeParameterTree);
                    extendsSlot = createVariable(location);
                    extendsMissingTrees.put(typeVarElement, extendsSlot);
                    logger.fine("Created variable for implicit extends on type parameter:\n" +
                            extendsSlot.getId() + " => " + typeVarElement + " (extends Object)");

                } else {
                    // Add annotation
                    extendsSlot = extendsMissingTrees.get(typeVarElement);
                }

                final AnnotatedTypeMirror upperBound = typeVar.getUpperBound();
                upperBound.addAnnotation(slotManager.getAnnotation(extendsSlot));
            }

        } else  {

            addExistentialVariable(typeVar, tree, false);
        }

        return null;
    }

    /**
     * Add a variable to the given primitiveType
     * @param primitiveType Type to annotate
     * @param tree Any tree type
     * @return null
     */
    @Override
    public Void visitPrimitive(AnnotatedPrimitiveType primitiveType, Tree tree) {
        if (tree instanceof BinaryTree) {
            // Since there are so many kinds of binary trees
            // handle these with an if instead of in the switch.
            handleBinaryTree(primitiveType, (BinaryTree)tree);
            return null;
        }

        if (tree instanceof VariableTree) {
            addPrimaryVariable(primitiveType, ((VariableTree) tree).getType());
        } else {
            addPrimaryVariable(primitiveType, tree);
        }

        return null;
    }

    /**
     * Add super/extends variable slots to the wildcardType.  Visits whatever bounds are available.
     * @param wildcardType type to annotate
     * @param tree A WildcardTree of kind: UNBOUNDED_WILDCARD, EXTENDS_WILDCARD, SUPER_WILDCARD
     * @return null
     */
    @Override
    public Void visitWildcard(AnnotatedWildcardType wildcardType, Tree tree) {

        if (!(tree instanceof WildcardTree)) {
            if (tree instanceof AnnotatedTypeTree) {
                tree = ((AnnotatedTypeTree) tree).getUnderlyingType();
            }
            if (!(tree instanceof WildcardTree)) {
                throw new IllegalArgumentException("Wildcard type ( " + wildcardType + " ) associated " +
                        "with non-WildcardTree ( " + tree + " ) ");
            }
        }

        // TODO: Despite what the framework docs say, if this WILDCARD is UNBOUNDED or EXTENDS bounded
        // TODO: then I believe the primary annotation is ignored.  Check this, if so then we might want to
        // TODO: either make it used (i.e. create a superBound) or just not generate the variable in this case
        final WildcardTree wildcardTree = (WildcardTree) tree;
        final Tree.Kind wildcardKind = wildcardTree.getKind();
        if (wildcardKind == Tree.Kind.UNBOUNDED_WILDCARD) {
            // Visit super bound, use the wild card type tree to represents the superbound.
            addPrimaryVariable(wildcardType.getSuperBound(), tree);

            // Visit extend bound, construct an artificial extends bound tree to represent the extendbound.
            ArtificialExtendsBoundTree artificialExtendsBoundTree = new ArtificialExtendsBoundTree(wildcardTree);
            addPrimaryVariable(wildcardType.getExtendsBound(), artificialExtendsBoundTree);

        } else if (wildcardKind == Tree.Kind.EXTENDS_WILDCARD) {
            addPrimaryVariable(wildcardType.getSuperBound(), tree);
            visit(wildcardType.getExtendsBound(), ((WildcardTree) tree).getBound());

        } else if (wildcardKind == Tree.Kind.SUPER_WILDCARD) {
            addPrimaryVariable(wildcardType.getExtendsBound(), tree);
            visit(wildcardType.getSuperBound(), ((WildcardTree) tree).getBound());
        }

        return null;
    }

    /**
     * Annotates the given methodType as a method declaration.  The return type, parameters, and type parameters of
     * the declaration are annotated but the body is NOT visited.
     * @param methodType A type to be annotated
     * @param tree A tree of METHOD kind, an IllegalArgumentException will be thrown otherwise
     * @return null
     */
    @Override
    public Void visitExecutable(AnnotatedExecutableType methodType, Tree tree) {
        InferenceUtil.testArgument(tree.getKind() == Tree.Kind.METHOD,
                "Unexpected tree type (" + tree + ") when visiting AnnotatedExecutableType (" + methodType + ")");

        boolean isFromAnonymousClass = ((MethodSymbol) methodType.getElement()).getEnclosingElement().isAnonymous();

        // This is so we do not add annotations the the parameters of a anonymous class invocation.
        if (((MethodSymbol)methodType.getElement()).isConstructor() && isFromAnonymousClass) {
            final MethodTree methodTree = (MethodTree) tree;
            final ExecutableElement methodElem = TreeUtils.elementFromDeclaration(methodTree);
            handleConstructorReturn(methodType, methodElem, (MethodTree) tree);
            handleReceiver(methodType, methodElem, methodTree, true);
            return null;
        }

        handleMethodDeclaration(methodType, (MethodTree) tree, isFromAnonymousClass);

        return null;
    }

    @Override
    public Void visitNull(AnnotatedNullType type, Tree tree) {
        addPrimaryVariable(type, tree);

        return null;
    }

    private void handleConstructorReturn(AnnotatedExecutableType methodType,
                                         ExecutableElement methodElem, MethodTree tree) {
        addPrimaryVariable(methodType.getReturnType(), tree);

        final AnnotatedDeclaredType returnType = (AnnotatedDeclaredType) methodType.getReturnType();
        // Use the element, don't try to use the tree
        // (since it might be in a different compilation unit, getting the path wont work)
        final AnnotatedDeclaredType classType  = inferenceTypeFactory.getAnnotatedType(ElementUtils.enclosingClass(methodElem));

        // TODO: TEST THIS
        // Copy the annotations from the class declaration type parameter to the return type params
        // although this might be handled by a methodFromUse etc...
        final List<AnnotatedTypeMirror> returnTypeParams = returnType.getTypeArguments();
        final List<AnnotatedTypeMirror> classTypeParams  = classType.getTypeArguments();
        assert returnTypeParams.size() == classTypeParams.size() : "Constructor type param size != class type param size";

        for (int i = 0; i < returnTypeParams.size(); i++) {
            CopyUtil.copyAnnotations(classTypeParams.get(i), returnTypeParams.get(i));
        }
    }

    private void handleReceiver(AnnotatedExecutableType methodType,
                                ExecutableElement methodElem, MethodTree methodTree, boolean anonymousClassReceiver) {
        final AnnotatedTypeMirror receiverType = methodType.getReceiverType();

        if (receiverType!= null && methodTree.getReceiverParameter() != null) {
            visit(methodType.getReceiverType(), methodTree.getReceiverParameter().getType());
        } else if (receiverType != null) {

            if (InferenceMain.isHackMode( ((MethodSymbol) methodElem).isConstructor())) {
                TypeElement enclosingClass = (TypeElement) methodElem.getEnclosingElement();

                if (((ClassSymbol) enclosingClass).isInner()) {
                    // Currently inner class constructors throw an exception in the AFU
                    addAnonymousClassReceiverAnnos(receiverType);
                    return;
                }
            }


            if (isAnnotatedFromBytecode(receiverType)) {
                return;

            // annotate missing tree if it's not a constructor or static
            } else if (!receiverMissingTrees.containsKey(methodElem)) {
                TreePath pathToMethod =  inferenceTypeFactory.getPath(methodTree);
                if (pathToMethod == null) {
                    pathToMethod = expensiveBackupGetPath(methodElem, methodTree, inferenceTypeFactory);
                }

                ASTRecord astRecord = ASTPathUtil.getASTRecordForPath(inferenceTypeFactory, pathToMethod);

                if (astRecord == null) {
                    if (!anonymousClassReceiver) {
                        TreePath path = inferenceTypeFactory.getPath(methodTree);
                        if (path != null) {
                            ASTRecord parent = ASTIndex.indexOf(path.getCompilationUnit()).get(path.getParentPath().getLeaf());

                            if (parent != null) {
                                astRecord = ASTPathUtil.getConstructorRecord(parent);
                            }
                        }
                    } else {
                        addAnonymousClassReceiverAnnos(receiverType);
                        return;
                    }

                    if (astRecord == null) {
                        throw new BugInCF("Missing path to receiver: " + methodElem + " => " + methodType);
                    }
                }

                ASTRecord toReceiver = astRecord.extend(Tree.Kind.METHOD, ASTPath.PARAMETER, -1);
                IdentityHashMap<AnnotatedTypeMirror, ASTRecord> typesToPaths =
                        ASTPathUtil.getImpliedRecordForUse(toReceiver, receiverType);

                for (Entry<AnnotatedTypeMirror, ASTRecord> typeToPath : typesToPaths.entrySet()) {
                    final AnnotatedTypeMirror type = typeToPath.getKey();
                    final ASTRecord path = typeToPath.getValue();

                    addImpliedPrimaryVariable(type, new AstPathLocation(path));
                }

                receiverMissingTrees.put(methodElem, receiverType.deepCopy());
                logger.fine("Created variable for implicit receiver on method:\n" + methodElem + "=>" + receiverType);


            } else {
                // Add annotation
                CopyUtil.copyAnnotations(receiverMissingTrees.get(methodElem), receiverType);
            }
        }
    }

    public void addAnonymousClassReceiverAnnos(AnnotatedTypeMirror receiverType) {

        // the receiver of an anonymous inner class method (including constructors) is the declared type of the
        // receiver type's class.  Use the receiver type to get this type.  Get the variable annotations from it
        // and copy them to the receiver since there is no way to write annotations that will
        // override the declaration
        final AnnotatedDeclaredType receiverDt = (AnnotatedDeclaredType)receiverType;
        final Element receiverClass = receiverDt.getUnderlyingType().asElement();
        final AnnotatedTypeMirror declarationType = inferenceTypeFactory.getAnnotatedType(receiverClass);

        // Note: We do not apply a primary annotation to the declaration of a class but we do
        // apply it to it's extends bound and therefore it's supertype.  Apply that
        AnnotationMirror variableAnno =
                declarationType.directSuperTypes().get(0).getAnnotationInHierarchy(varAnnot);


        if (variableAnno == null) {
            if (!InferenceMain.isHackMode()) {
                throw new BugInCF("Missing receiver annotation: " + receiverType + "  " + declarationType);
            }
        } else {
            receiverType.replaceAnnotation(variableAnno);
        }

        // copy any annotations on type parameters
        // we might want to add an ExistentialVariable here in the future but for now
        // it has no meaning
        CopyUtil.copyAnnotations(declarationType, receiverType);
    }

    /**
     * TODO: ADD TESTS FOR <>
     * Annotates the return type, parameters, and type parameter of the given method declaration.
     * methodElement -> methodType
     * @param methodType
     * @param tree
     */
    private void handleMethodDeclaration(final AnnotatedExecutableType methodType, final MethodTree tree,
                                         boolean isFromAnonymousClass) {
        // TODO: DOES THIS CHANGE WITH JAVA 8 AND CLOSURES?
        final MethodTree methodTree = tree;
        final ExecutableElement methodElem = TreeUtils.elementFromDeclaration(methodTree);
        final boolean isConstructor = TreeUtils.isConstructor(tree);

        // this needs to happen before anythinge els because they might be referred to in other types
        visitTogether(methodType.getTypeVariables(), methodTree.getTypeParameters());  // TODO: STORE THESE TYPES?

        if (isConstructor) {
            handleConstructorReturn(methodType, methodElem, tree);

        } else if (methodType.getReturnType() != null) {
            visit(methodType.getReturnType(), tree.getReturnType());
        }

        handleReceiver(methodType, methodElem, methodTree, isFromAnonymousClass);

        // Handle parameters
        final List<Tree> paramTrees = new ArrayList<>(methodTree.getParameters().size());
        for (final VariableTree paramTree : methodTree.getParameters()) {
            paramTrees.add(paramTree);
        }
        visitTogether(methodType.getParameterTypes(), paramTrees);     // TODO: STORE THESE TYPES?

        storeElementType(methodElem, methodType);
    }

    private ASTRecord recreateImpliedReceiverASTRecord(MethodTree methodTree) {
        // TODO:
        return null;
    }

    /**
     * Annotate a BinaryTree by creating and storing the LUB of the elemtns.
     * @param atm the type of the binary tree to annotate
     * @param binaryTree the binary tree
     */
    public void handleBinaryTree(AnnotatedTypeMirror atm, BinaryTree binaryTree) {

        if (treeToVarAnnoPair.containsKey(binaryTree)) {
            atm.replaceAnnotations(treeToVarAnnoPair.get(binaryTree).second);
        } else {
            AnnotatedTypeMirror a = inferenceTypeFactory.getAnnotatedType(binaryTree.getLeftOperand());
            AnnotatedTypeMirror b = inferenceTypeFactory.getAnnotatedType(binaryTree.getRightOperand());
            Set<? extends AnnotationMirror> lubs = inferenceTypeFactory
                    .getQualifierHierarchy().leastUpperBounds(a.getEffectiveAnnotations(),
                            b.getEffectiveAnnotations());
            atm.clearAnnotations();
            atm.addAnnotations(lubs);
            if (slotManager.getVariableSlot(atm).isVariable()) {
                final Pair<VariableSlot, Set<? extends AnnotationMirror>> varATMPair = Pair.<VariableSlot, Set<? extends AnnotationMirror>>of(
                        slotManager.getVariableSlot(atm), lubs);
                treeToVarAnnoPair.put(binaryTree, varATMPair);
            } else {
                // The slot returned was a constant. Regenerating it is ok.
            }
        }
    }

    /**
     * If the given declaration tree of element has been previously annotated by the VariableAnnotator
     * then copy the annotations from a stored AnnotatedTypeMirror onto destAtm
     * @param element The element which may or may not correspond to a tree which has already been annotated
     * @param destAtm The type of element
     * @return True if destAtm was annotated, false otherwise
     */
    public boolean annotateElementFromStore(final Element element, final AnnotatedTypeMirror destAtm) {
        if (!elementToAtm.containsKey(element)) {
            return false;
        }

        final AnnotatedTypeMirror srcAtm = elementToAtm.get(element);
        CopyUtil.copyAnnotations(srcAtm, destAtm);

        return true;
    }

    public void annotateImpliedType(AnnotatedTypeMirror type, boolean isUse, ASTRecord parent) {
        impliedTypeAnnotator.annotate(type, isUse, parent);
    }

    /**
     * Given a list of types and tree, visit them pairwise with this VariableAnnotator.  The sizes of types and
     * trees MUST be equal
     * @param types A list of types to visit
     * @param trees A list of trees to visit
     */
    private void visitTogether(final List<? extends AnnotatedTypeMirror> types, final List<? extends Tree> trees) {
        assert types.size() == trees.size();

        for (int i = 0; i < types.size(); ++i) {
            visit(types.get(i), trees.get(i));
        }
    }

    private boolean isAnnotatedFromBytecode(AnnotatedTypeMirror typeMirror) {

        class VarAnnotScanner extends AnnotatedTypeScanner<Boolean, Void> {

            @Override
            protected Boolean reduce(Boolean r1, Boolean r2) {
                if (r1 == null && r2 == null) {
                    return false;
                }

                if (r1 == null) {
                    return r2;
                }

                if (r2 == null) {
                    return r1;
                }

                return r1 || r2;
            }

            @Override
            protected Boolean scan(AnnotatedTypeMirror type, Void aVoid) {
                if (InferenceQualifierHierarchy.findVarAnnot(type.getAnnotations()) != null) {
                    return true;
                }
                Boolean superCall = super.scan(type, aVoid);
                if (superCall == null) { // handles null returns from things like scanning an empty list of type args
                    return false;
                }

                return superCall;
            }
        }

        Boolean result = new VarAnnotScanner().visit(typeMirror, null);
        if (result == null) {
            return false;
        }

        return result;
    }

    /**
     * This method returns the annotation that may or may not be placed on the class declaration for type.
     * If it does not already exist, this method creates the annotation and stores it in classDeclAnnos.
     */
    private VariableSlot getOrCreateDeclBound(AnnotatedDeclaredType type) {

        TypeElement classDecl = (TypeElement) type.getUnderlyingType().asElement();

        VariableSlot topConstant = getTopConstant();
        VariableSlot declSlot = classDeclAnnos.get(classDecl);
        if (declSlot == null) {
            Tree decl = inferenceTypeFactory.declarationFromElement(classDecl);
            if (decl != null) {
                VariableSlot potentialDeclSlot = createVariable(decl);
                declSlot = getOrCreateExistentialVariable(potentialDeclSlot, topConstant);
                classDeclAnnos.put(classDecl, potentialDeclSlot);

            } else {
                declSlot = topConstant;
            }
        }

        return declSlot;
    }

    private void addDeclarationConstraints(VariableSlot declSlot, VariableSlot instanceSlot) {
        constraintManager.addSubtypeConstraint(instanceSlot, declSlot);
    }

    public void clearTreeInfo() {
        // We have never cleared the tree -> VarSlot cache, can we?
        // This has been used to ensure we don't add new variables to trees that are visited twice
        // but, since we now store annotations in bytecode, shouldn't this not be a problem?
        treeToPolyVar.clear();
    }
}
