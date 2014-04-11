package checkers.inference;

import static checkers.inference.util.InferenceUtil.isAnonymousClass;
import static checkers.inference.util.InferenceUtil.join;
import static checkers.inference.util.InferenceUtil.testArgument;

import java.util.List;

import javax.lang.model.element.Element;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNoType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.TreeAnnotator;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.TreeUtils;

import checkers.inference.util.InferenceUtil;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;

/**
 * InferenceTreeAnnotator (a non-traversing visitor) determines which trees need to be annotated and then passes them
 * (along with their types) to the VariableAnnotator which will do a deep traversal of the tree/type.
 * VariableAnnotator will create the appropriate VariableSlots, store them via Tree -> VariableSlot, and place
 * annotations representing the VariableSlots onto the AnnotateTypeMirror.
 */
public class InferenceTreeAnnotator extends TreeAnnotator {

    private final SlotManager slotManager;
    private final VariableAnnotator variableAnnotator;
    private final AnnotatedTypeFactory realTypeFactory;
    private final InferrableChecker realChecker;

    //TODO: In the old InferenceAnnotatedTypeFactory there was a store between extends/impelement identifier expressions
    //TODO: used for getTypeFromTypeTree, I believe this is superfluous (since they will already be placed in
    //TODO: AnnotatedTypeFactory) but I am unsure, therefore, we'll leave these todos and circle back
    //private Map<Tree, AnnotatedTypeMirror> extendsAndImplementsTypes = new HashMap<Tree, AnnotatedTypeMirror>();

    public InferenceTreeAnnotator(final InferenceAnnotatedTypeFactory atypeFactory,
                                  final InferrableChecker realChecker,
                                  final AnnotatedTypeFactory realAnnotatedTypeFactory,
                                  final VariableAnnotator variableAnnotator,
                                  final SlotManager slotManager) {
        super(atypeFactory);
        this.slotManager = slotManager;
        this.variableAnnotator = variableAnnotator;
        this.realTypeFactory = realAnnotatedTypeFactory;
        this.realChecker = realChecker;
    }

    /**
     * Add variables to class declarations of non-anonymous classes
     * @param classTree tree to visit, will be ignored if it's an anonymous class
     * @param classType AnnotatedDeclaredType, an Illegal argument exception will be throw otherwise
     * @see checkers.inference.VariableAnnotator#handleClassDeclaration(checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType, com.sun.source.tree.ClassTree)
     * @return null
     */
    @Override
    public Void visitClass(final ClassTree classTree, final AnnotatedTypeMirror classType) {
        // Apply Implicits
        super.visitClass(classTree, classType);

        testArgument(classType instanceof AnnotatedDeclaredType,
                "Unexpected type for ClassTree ( " + classTree + " ) AnnotatedTypeMirror ( " + classType + " ) ");

        // For anonymous classes, we do not create additional variables, as they
        // were already handled by the visitNewClass. This would otherwise result
        // in new variables for an extends clause, which then cannot be inserted.
        if (!isAnonymousClass(classTree)) {
            this.variableAnnotator.visit(classType, classTree);
        }

        return null;
    }

    /**
     * Adds variables to the upper and lower bounds of a typeParameter
     */
    @Override
    public Void visitTypeParameter(final TypeParameterTree typeParamTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitTypeParameter(typeParamTree, atm);

        testArgument(atm instanceof AnnotatedTypeVariable,
                "Unexpected type for TypeParamTree ( " + typeParamTree + " ) AnnotatedTypeMirror ( " + atm + " ) ");

        variableAnnotator.visit(atm, typeParamTree);

        return null;
    }

    /**
     * @see checkers.inference.VariableAnnotator#handleMethodDeclaration(checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType, com.sun.source.tree.MethodTree)
     */
    @Override
    public Void visitMethod(final MethodTree methodTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitMethod(methodTree, atm);

        testArgument(atm instanceof AnnotatedExecutableType,
                "Unexpected type for MethodTree ( " + methodTree + " ) AnnotatedTypeMirror ( " + atm + " ) ");

        variableAnnotator.visit(atm, methodTree);

        return null;
    }

    /**
     * Adds variables to the methodTypeArguments
     * //TODO: Verify that return types for generic methods work correctly
     */
    @Override
    public Void visitMethodInvocation(final MethodInvocationTree methodInvocationTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitMethodInvocation(methodInvocationTree, atm);

        //inferTypeArguments sometimes passes annotatateImplicit(methodInvocationTree, atm)
        if(atm instanceof AnnotatedNoType) {
            return null;
        }

        annotateMethodTypeArgs(methodInvocationTree);

        return null;
    }


    private void annotateMethodTypeArgs(final MethodInvocationTree methodInvocationTree) {

        if(!methodInvocationTree.getTypeArguments().isEmpty()) {
            final Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> methodFromUse =
                    atypeFactory.methodFromUse(methodInvocationTree);

            annotateMethodTypeArguments(methodInvocationTree.getTypeArguments(), methodFromUse.second);
        } else {
            //TODO: annotate types if there are types but no trees
        }
    }

    private void annotateMethodTypeArgs(final NewClassTree newClassTree) {

        if(!newClassTree.getTypeArguments().isEmpty()) {
            final Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> constructorFromUse =
                    atypeFactory.constructorFromUse(newClassTree);

            annotateMethodTypeArguments(newClassTree.getTypeArguments(), constructorFromUse.second);
        } else {
            //TODO: annotate types if there are types but no trees
        }
    }

    private void annotateMethodTypeArguments(final List<? extends Tree> typeArgTrees,
                                              final List<AnnotatedTypeMirror> typeArgs) {
        if(!typeArgTrees.isEmpty()) {
            assert typeArgs.size() == typeArgTrees.size() : "Number of type argument trees differs from number of types!" +
                    "Type arguments ( " + join(typeArgs) +
                    "Trees ( " + join(typeArgTrees);
            for(int i = 0; i < typeArgs.size(); i++) {
                variableAnnotator.visit(typeArgs.get(i), typeArgTrees.get(i));
            }
        }
    }

    //TODO: DOES THIS DO WHAT WE WANT TO DO, I.E. FOR ANONYMOUS CLASSES DO WE EVER ADD THE TYPES TO ATM
    @Override
    public Void visitNewClass(final NewClassTree newClassTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitNewClass(newClassTree,  atm);

        //Anonymous classes implicitly either extend or implement a class (e.g. new Runnable(){...} implements Runnable)
        if(isAnonymousClass(newClassTree)) {
            final ClassTree body = newClassTree.getClassBody();

            final Tree superTree;
            final Tree extendsTree = body.getExtendsClause();
            if(extendsTree != null) {
                superTree = extendsTree;
            } else {
                final List<? extends Tree> implTrees = body.getImplementsClause();
                assert implTrees.size() == 1 : "Anonymous classes must exactly 1 extends or implements clause: " +
                        "tree ( " + newClassTree + " ) ";
                superTree = implTrees.get(0);
            }

            final AnnotatedTypeMirror superType = atypeFactory.getAnnotatedType(superTree);
            variableAnnotator.visit(superType, superTree);

            //TODO: SEE OLD InferenceTreeAnnotator copying directSuperTypes annotations


        } else {
            variableAnnotator.visit(atm, newClassTree.getIdentifier());
        }
        annotateMethodTypeArgs(newClassTree);

        return null;
    }

    @Override
    public Void visitVariable(final VariableTree varTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitVariable(varTree, atm);

        if (InferenceUtil.isDetachedVariable(varTree)) {
            return null;
        }
        //TODO: Here is where we would decide what tree to use in getPath, probably we look up the
        //TODO: path to the original varTree and handle it appropriately

        variableAnnotator.visit(atm, varTree.getType());

        final Element varElem = TreeUtils.elementFromDeclaration(varTree);

        //TODO: THIS AND THE VISIT BINARY COULD INSTEAD BE PUT AT THE TOP OF THE VISIT METHOD OF VariableAnnotator
        //TODO: AS SPECIAL CASES, THIS WOULD MEAN WE COULD LEAVE storeElementType and addPrimaryCombVar AS PRIVATE
        //This happens here, unlike all the other stores because then we would have to add this code
        //to every atm/varTree combination, thoughts?
        switch (varElem.getKind()) {
            case ENUM_CONSTANT:
            case FIELD:
            case LOCAL_VARIABLE:
            case PARAMETER:
            case EXCEPTION_PARAMETER:
                variableAnnotator.storeElementType(varElem, atm);
                break;

            default:
                ErrorReporter.errorAbort("Unexpected element of kind ( " + varElem.getKind() + " ) element ( " + varElem + " ) ");
        }
        return null;
    }

    @Override
    public Void visitNewArray(final NewArrayTree newArrayTree, final AnnotatedTypeMirror atm) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        testArgument(atm instanceof AnnotatedArrayType,
                "Unexpected type for NewArrayTree ( " + newArrayTree + " ) AnnotatedTypeMirror ( " + atm + " ) ");
        variableAnnotator.visit(atm, newArrayTree);
        return null;
    }

    @Override
    public Void visitTypeCast(final TypeCastTree typeCast, final AnnotatedTypeMirror atm) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        variableAnnotator.visit(atm, typeCast.getType());
        return null;
    }

    @Override
    public Void visitInstanceOf(final InstanceOfTree instanceOfTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitInstanceOf(instanceOfTree, atm);

        //atm is always boolean, get actual tested type
        final AnnotatedTypeMirror testedType = atypeFactory.getAnnotatedType(instanceOfTree.getType());
        variableAnnotator.visit(testedType, instanceOfTree.getType());
        return null;
    }

    @Override
    public Void visitLiteral(final LiteralTree literalTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitLiteral(literalTree, atm);
        variableAnnotator.visit(atm, literalTree);
        return null;
    }

    /**
     * The type returned from a unary operation is just the variable.
     * This will have to change if we support refinement variables.
     */
    @Override
    public Void visitUnary(UnaryTree node, AnnotatedTypeMirror type) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        AnnotatedTypeMirror exp = atypeFactory.getAnnotatedType(node.getExpression());
        type.addMissingAnnotations(exp.getAnnotations());
        return null;
    }

    /**
     * The type returned from a compound operation is just the variable.
     * The visitor will insure that the RHS is a subtype of the LHS of the
     * compound assignment.
     *
     * This will have to change if we support refinement variables.
     */
    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, AnnotatedTypeMirror type) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        AnnotatedTypeMirror exp = atypeFactory.getAnnotatedType(node.getExpression());
        type.addMissingAnnotations(exp.getAnnotations());
        return null;
    }

    /**
     * We need to create a LUB and only create it once.
     */
    @Override
    public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        // Unary trees and compound assignments (x++ or x +=y) get desugared
        // by dataflow to be x = x + 1 and x = x + y.
        // Dataflow will then look up the types of the binary operations (x + 1) and (y + 1)
        //
        // InferenceTransfer currently sets the value of a compound assignment or unary 
        // to be the just the type of the variable.
        // So, the type returned from this for desugared trees is not used.
        // We don't create a LUB to reduce confusion
        if (realTypeFactory.getPath(node) == null) {
            // Desugared tree's don't have paths.
            return null;
        }

        variableAnnotator.visit(type, node);
        return null;
    }
}
