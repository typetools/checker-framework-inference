package checkers.inference;

import static checkers.inference.util.InferenceUtil.*;
import static checkers.inference.util.CopyUtil.*;

import annotations.io.ASTPath;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.ASTPathUtil;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.*;
import checkers.types.visitors.AnnotatedTypeScanner;
import com.sun.source.tree.*;

import javacutils.ElementUtils;
import javacutils.TreeUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


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

    private static final Logger logger = LoggerFactory.getLogger(VariableAnnotator.class);

    private final InferenceAnnotatedTypeFactory inferenceTypeFactory;
    private final SlotManager slotManager;

    // Variable Annotator needs this to create equality constraints for pre-annotated and
    // implicit code
    private ConstraintManager constraintManager;

    //need to create a cache for non-declarations and declarations?
    //clear the ones that we couldn't possibly need later?
    private final Map<Tree, VariableSlot> treeToVariable;
    private final Map<Element, AnnotatedTypeMirror> elementToAtm;
    private final AnnotatedTypeFactory realTypeFactory;
    private final InferrableChecker realChecker;

    public VariableAnnotator(final InferenceAnnotatedTypeFactory typeFactory,
                              final AnnotatedTypeFactory realTypeFactory,
                              final InferrableChecker realChecker,
                              final SlotManager slotManager, ConstraintManager constraintManager) {
        this.realTypeFactory = realTypeFactory;
        this.inferenceTypeFactory = typeFactory;
        this.slotManager = slotManager;
        this.treeToVariable = new HashMap<>();
        this.elementToAtm   = new HashMap<>();
        this.realChecker = realChecker;
        this.constraintManager = constraintManager;
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
        final VariableSlot varSlot = createVariable(ASTPathUtil.getASTPathToNode(inferenceTypeFactory, tree));
        treeToVariable.put(tree, varSlot);
        logger.debug("Created variable for tree:\n" + varSlot.getId() + " => " + tree);
        return varSlot;
    }

    /**
     * Creates a variable with the given ASTPath, adds it to the slotManager, and returns it.  This method
     * should be used ONLY for "implied trees".  That is, locations that don't exist in the source code
     * but are implied by other trees.  The created variable is also added to the SlotManager
     * (e.g. the "extends Object" bound that is implied by <T> in the declaration class MyClass<T> extends List<T>{}).
     *
     * @param astPath The path to the "missing tree". That is, the path to the parent tree with the path to the
     *                actual implied tree appended to it.
     * @return A new VariableSlot corresponding to tree
     */
    private VariableSlot createVariable(final ASTPath astPath) {
        final VariableSlot variable = new VariableSlot(astPath, slotManager.nextId());
        slotManager.addVariable(variable);
        return variable;
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
    private void addPrimaryVariable(AnnotatedTypeMirror atm, final Tree tree) {
        final VariableSlot variable;
        if(treeToVariable.containsKey(tree)) {
            variable = treeToVariable.get(tree);
        } else {
            variable = createVariable(tree);
            Slot equivalentSlot = null;
            // Create constraints for pre-annotated code and constant slots when the variable slot is created.
            if(!atm.getAnnotations().isEmpty()) {
                assert atm.getAnnotations().size() <= 1 : ("Old Inference code expected that there might be multiple annotations" +
                        " on a given atm.  This is a conservative attempt to figure out why! " + tree);

                equivalentSlot = slotManager.getSlot(atm);
            } else {
                // Considered constant by real type system
                if(realChecker.isConstant(atm) ) {
                    equivalentSlot = slotManager.getSlot(realTypeFactory.getAnnotatedType(tree));
                }
            }

            if(equivalentSlot != null && !equivalentSlot.equals(variable)) {
                constraintManager.add(new EqualityConstraint(equivalentSlot, variable));
            }
        }

        atm.clearAnnotations();
        atm.addAnnotation(slotManager.getAnnotation(variable));
    }

    /**
     * Creates a CombVariableSlot between the annotations on the left/right side of the binaryTree.  The
     * CombVariableSlot is added to the slotManager.
     *
     * @param atm type of the binaryTree that will hold the annotation representing the CombVariableSlot
     * @param binaryTree tree containing two slots two combine.  The ASTPath for the created variable will point
     *                   to this binaryTree
     */
    public void addPrimaryCombVar(AnnotatedTypeMirror atm, final BinaryTree binaryTree) {
        final VariableSlot variable;

        if(treeToVariable.containsKey(binaryTree)) {
            variable = treeToVariable.get(binaryTree);

        } else {
            final Slot slot1 = slotManager.getSlot(inferenceTypeFactory.getAnnotatedType(binaryTree.getLeftOperand()));
            final Slot slot2 = slotManager.getSlot(inferenceTypeFactory.getAnnotatedType(binaryTree.getRightOperand()));

            final ASTPath astPath = ASTPathUtil.getASTPathToNode(inferenceTypeFactory, binaryTree);
            variable = new CombVariableSlot(astPath, slotManager.nextId(), slot1, slot2);
            slotManager.addVariable(variable);

        }

        atm.clearAnnotations();
        atm.addAnnotation(slotManager.getAnnotation(variable)) ;
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
     *             ANNOTATION_TYPE, CLASS, INTERFACE, ENUM, STRING_LITERAL, IDENTIFIER, ANNOTATED_TYPE, TYPE_PARAMETER, MEMBER_SELECT, PARAMETERIZED_TYPE
     * @return null
     */
    @Override
    public Void visitDeclared(final AnnotatedDeclaredType adt, final Tree tree) {

        switch(tree.getKind()) {
            case ANNOTATION_TYPE:
            case CLASS:
            case INTERFACE:
            case ENUM: //TOOD: MORE TO DO HERE?
                handleClassDeclaration(adt, (ClassTree) tree);
                break;

            case STRING_LITERAL:
            case IDENTIFIER:
                addPrimaryVariable(adt, tree);
                break;
            case ANNOTATED_TYPE:
                //TODO: Old InferenceTreeAnnotator did nothing, is that ok? I think so because the actual type will
                //TODO: be visited
                break;

            case TYPE_PARAMETER:
                //TODO: I assume that the only way a TypeParameterTree is going to have an ADT as its
                //TODO: AnnotatedTypeMirror is through either a getEffectiveAnnotation call or some other
                //TODO: call that will treat the type parameter as it's upper bound but we should probably
                //TODO: inspect this in order to have an idea of when this happens

                final TypeParameterTree typeParamTree = (TypeParameterTree) tree;

                if(typeParamTree.getBounds().isEmpty()) {
                    addPrimaryVariable(adt, tree);
                    //TODO: HANDLE MISSING EXTENDS BOUND?
                } else {
                    visit(adt, typeParamTree.getBounds().get(0));
                }
                break;

            case MEMBER_SELECT:
                addPrimaryVariable(adt, tree);
                visit(adt.getEnclosingType(), ((MemberSelectTree) tree).getExpression());
                break;

            case PARAMETERIZED_TYPE:
                final ParameterizedTypeTree parameterizedTypeTree = (ParameterizedTypeTree) tree;
                visit(adt, parameterizedTypeTree.getType());

                final List<? extends Tree> treeArgs = parameterizedTypeTree.getTypeArguments();
                final List<AnnotatedTypeMirror> typeArgs = adt.getTypeArguments();

                //TODO: RAWNESS? We probably want to do something to this to fix it up
                assert treeArgs.size() == typeArgs.size() : "Raw type? Tree(" + parameterizedTypeTree + "), Atm(" + adt + ")";

                for(int i = 0; i < typeArgs.size(); i++) {
                    final AnnotatedTypeMirror typeArg = typeArgs.get(i);
                    visit(typeArg, treeArgs.get(i));

                    if(typeArg instanceof AnnotatedDeclaredType) {
                        final TypeElement typeElement =
                                (TypeElement) ((DeclaredType) typeArg.getUnderlyingType()).asElement();
                        storeElementType(typeElement, typeArg);
                    }
                }
                break;

            default:
                throw new IllegalArgumentException("Unexpected tree type ( kind=" + tree.getKind() + " tree= " + tree + " ) when visiting " +
                        "AnnotatedDeclaredType( " + adt +  " )");
        }

        return null;
    }

    /**
     * Visit the extends, implements, and type parameters of the given class type and tree.
     */
    private void handleClassDeclaration(AnnotatedDeclaredType classType, ClassTree classTree) {
        final Tree extendsTree = classTree.getExtendsClause();
        if(extendsTree == null) {
            //add missing class

        } else {
            final AnnotatedTypeMirror extendsType = inferenceTypeFactory.getAnnotatedTypeFromTypeTree(extendsTree);
            visit(extendsType, extendsTree);
        }

        //TODO: NOT SURE THIS HANDLES MEMBER SELECT CORRECTLY
        for(Tree implementsTree : classTree.getImplementsClause()) {
            final AnnotatedTypeMirror impelementsType = inferenceTypeFactory.getAnnotatedTypeFromTypeTree(implementsTree);
            visit(impelementsType, implementsTree);
        }

        visitTogether(classType.getTypeArguments(), classTree.getTypeParameters());
    }

    /**
     * Visit each bound on the intersection type
     * @param intersectionType type to annotate
     * @param tree An AnnotatedIntersectionTypeTree, an IllegalArgumentException will be thrown otherwise
     * @return null
     */
    @Override
    public Void visitIntersection(AnnotatedIntersectionType intersectionType, Tree tree) {

        // TODO: Don't COMMIT!
        if (!(tree instanceof AnnotatedIntersectionType)) {
            return null;
        }

        //TODO: THERE ARE PROBABLY INSTANCES OF THIS THAT I DON'T KNOW ABOUT, CONSULT WERNER
        //TODO: AND DO GENERAL TESTING/THINKING ABOUT WHAT WE WANT TO DO WITH INTERSECTIONS
        testArgument(tree instanceof AnnotatedIntersectionType,
            "Unexpected tree type ( " + tree + " ) when visiting AnnotatedIntersectionType( " + intersectionType +  " )");

        //TODO: So in Java 8 the Ast the "A & B" tree in T extends A & B is an IntersectionTypeTree
        //TODO: but there are also casts of type (A & B) I believe
        visitTogether(intersectionType.directSuperTypes(), ((IntersectionTypeTree) tree).getBounds());

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

        testArgument(tree instanceof UnionTypeTree,
            "Unexpected tree type ( " + tree + " ) for AnnotatedUnionType (" + unionType + ")");

        final List<? extends Tree> unionTrees = ((UnionTypeTree) tree).getTypeAlternatives();
        final List<AnnotatedDeclaredType> unionTypes = unionType.getAlternatives();
        for(int i = 0; i < unionTypes.size(); i++) {
            visit(unionTypes.get(i), unionTrees.get(i));
        }

        return null;
    }

    /**
     * Annotates the array type of the given AnnotatedArrayType.  The component type is also annotated
     * UNLESS the tree represents an array literal (though this is something that is potentially inferred).
     * TODO:  Perhaps the component type is the lub of the initializers to start?
     * @param type The type to be annotated
     * @param tree A tree of kind: ARRAY_TYPE, NEW_ARRAY, ANNOTATION_TYPE
     *             an IllegalArgumentException is thrown otherwise
     * @return null
     */
    @Override
    public Void visitArray(AnnotatedArrayType type, Tree tree) {

        final Tree componentTree;
        boolean isArrayLiteral = false;
        switch(tree.getKind()) {
            case ARRAY_TYPE:
                componentTree = ((ArrayTypeTree) tree).getType();
                break;

            case NEW_ARRAY:
                componentTree = ((NewArrayTree) tree).getType();
                isArrayLiteral = componentTree == null;
                break;

            case ANNOTATION_TYPE:
                componentTree = tree;
                break;

            default:
                // TODO: DO NOT COMMIT THIS.
                return null;
//                throw new IllegalArgumentException("Unexpected tree (" + tree + ") for type (" + type + ")");
        }

        //TODO: NOTE, this means that the component types of array literal gets a type even though there is no location
        //TODO: to place an annotation on it, leave it off?
        addPrimaryVariable(type, tree);
        if(!isArrayLiteral) {
            visit(type.getComponentType(), componentTree);
        }

        return null;
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

        if(tree.getKind() == Tree.Kind.TYPE_PARAMETER) {
            final TypeParameterElement typeParamElement = (TypeParameterElement) typeVar.getUnderlyingType().asElement();
            final TypeParameterTree typeParameterTree   = (TypeParameterTree) tree;

            addPrimaryVariable(typeVar, tree); //add lower bound var
            if(typeParameterTree.getBounds().size() > 0) {
                visit(typeVar.getUpperBound(), typeParameterTree.getBounds().get(0));
            } else {
                //TODO: add missing tree var
            }

            storeElementType(typeParamElement, typeVar);

        } else  { //TODO: This is a type use
            addPrimaryVariable(typeVar, tree);

            if(tree instanceof VariableTree) { //if it's a declaration of a variable, store it
                final Element varElement = TreeUtils.elementFromDeclaration((VariableTree) tree);
                storeElementType(varElement, typeVar);
            }
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
        addPrimaryVariable(primitiveType, tree);
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

        if(!(tree instanceof WildcardTree)) {
            throw new IllegalArgumentException("Wildcard type ( " + wildcardType + " ) associated " +
                    "with non-WildcardTree ( " + tree + " ) ");
        }

        //TODO: Despite what the framework docs say, if this WILDCARD is UNBOUNDED or EXTENDS bounded
        //TODO: then I believe the primary annotation is ignored.  Check this, if so then we might want to
        //TODO: either make it used (i.e. create a superBound) or just not generate the variable in this case
        final WildcardTree wildcardTree = (WildcardTree) tree;
        final Tree.Kind wildcardKind = wildcardTree.getKind();
        if(wildcardKind == Tree.Kind.UNBOUNDED_WILDCARD) {
            addPrimaryVariable(wildcardType, tree);

            //TODO: Add missing extends bounds

        } else if(wildcardKind == Tree.Kind.EXTENDS_WILDCARD) {
            addPrimaryVariable(wildcardType, tree);
            visit(wildcardType.getExtendsBound(), ((WildcardTree) tree).getBound());

        } else if(wildcardKind == Tree.Kind.SUPER_WILDCARD) {
            addPrimaryVariable(wildcardType.getExtendsBound(), tree);
            visit(wildcardType.getExtendsBound(), ((WildcardTree) tree).getBound());
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
        testArgument(tree.getKind() == Tree.Kind.METHOD,
                "Unexpected tree type (" + tree + ") when visiting AnnotatedExecutableType (" + methodType + ")");
        handleMethodDeclaration(methodType, (MethodTree) tree);

        return null;
    }

    @Override
    public Void visitNull(AnnotatedNullType type, Tree tree) {
        addPrimaryVariable(type, tree);

        return null;
    }

    /**
     * TODO: ADD TESTS FOR <>
     * Annotates the return type, parameters, and type parameter of the given method declaration.  
     * methodElement -> methodType
     * @param methodType
     * @param tree
     */
    private void handleMethodDeclaration(final AnnotatedExecutableType methodType, final MethodTree tree) {
        //TODO: DOES THIS CHANGE WITH JAVA 8 AND CLOSURES?
        final MethodTree methodTree = (MethodTree) tree;
        final ExecutableElement methodElem = TreeUtils.elementFromDeclaration(methodTree);
        final boolean isConstructor = TreeUtils.isConstructor((MethodTree) tree);

        if (isConstructor) {
            addPrimaryVariable(methodType.getReturnType(), tree);
            ClassTree classTree = TreeUtils.enclosingClass(inferenceTypeFactory.getPath(tree));

            final AnnotatedDeclaredType returnType = (AnnotatedDeclaredType) methodType.getReturnType();
            final AnnotatedDeclaredType classType;
            if (classTree != null) {
                classType = inferenceTypeFactory.getAnnotatedType(classTree);
            } else {
                // Use the element, don't try and use the tree.
                classType = inferenceTypeFactory.getAnnotatedType(ElementUtils.enclosingClass(methodElem));
            }

            //TODO: TEST THIS
            //Copy the annotations from the class declaration type parameter to the return type params
            //although this might be handled by a methodFromUse etc...
            final List<AnnotatedTypeMirror> returnTypeParams = returnType.getTypeArguments();
            final List<AnnotatedTypeMirror> classTypeParams  = classType.getTypeArguments();
            assert returnTypeParams.size() == classTypeParams.size() : "Constructor type param size != class type param size";

            for (int i = 0; i < returnTypeParams.size(); i++) {
                copyAnnotations(classTypeParams.get(i), returnTypeParams.get(i));
            }

        } else if(methodType.getReturnType() != null) {
            visit(methodType.getReturnType(), tree.getReturnType());
        }

        visitTogether(methodType.getTypeVariables(), methodTree.getTypeParameters());  //TODO: STORE THESE TYPES?

        if (methodType.getReceiverType() != null && methodTree.getReceiverParameter() != null) {
            visit(methodType.getReceiverType(), methodTree.getReceiverParameter().getType());
        } else {
            //annotate missing tree if it's not a constructor or static
        }

        //Handle parameters
        final List<Tree> paramTrees = new ArrayList<>(methodTree.getParameters().size());
        for(final VariableTree paramTree : methodTree.getParameters()) {
            paramTrees.add(paramTree.getType());
        }
        visitTogether(methodType.getParameterTypes(), paramTrees);     //TODO: STORE THESE TYPES?

        storeElementType(methodElem, methodType);
    }

    /**
     * If the given declaration tree of element has been previously annotated by the VariableAnnotator
     * then copy the annotations from a stored AnnotatedTypeMirror onto destAtm
     * @param element The element which may or may not correspond to a tree which has already been annotated
     * @param destAtm The type of element
     * @return True if destAtm was annotated, false otherwise
     */
    public boolean annotateElementFromStore(final Element element, final AnnotatedTypeMirror destAtm) {
        if(!elementToAtm.containsKey(element)) {
            return false;
        }

        final AnnotatedTypeMirror srcAtm = elementToAtm.get(element);

        if(element instanceof ExecutableElement) {
            copyParameterAndReturnTypes((AnnotatedExecutableType) srcAtm, (AnnotatedExecutableType) destAtm);
        } else {
            copyAnnotations(srcAtm, destAtm);
        }

        return true;
    }

    /**
     * Given a list of types and tree, visit them pairwise with this VariableAnnotator.  The sizes of types and
     * trees MUST be equal
     * @param types A list of types to visit
     * @param trees A list of trees to visit
     */
    private void visitTogether(final List<? extends AnnotatedTypeMirror> types, final List<? extends Tree> trees) {
        assert types.size() == trees.size();

        for(int i = 0; i < types.size(); i++) {
            visit(types.get(i), trees.get(i));
        }
    }
}
