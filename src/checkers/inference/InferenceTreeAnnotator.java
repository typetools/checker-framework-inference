package checkers.inference;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeFactory.ParameterizedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNoType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.TreeUtils;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import checkers.inference.util.ConstantToVariableAnnotator;
import checkers.inference.util.InferenceUtil;

/**
 * InferenceTreeAnnotator (a non-traversing visitor) determines which trees need to be annotated and then passes them
 * (along with their types) to the VariableAnnotator which will do a deep traversal of the tree/type.
 * VariableAnnotator will create the appropriate VariableSlots, store them via Tree -> VariableSlot, and place
 * annotations representing the VariableSlots onto the AnnotateTypeMirror.
 */
public class InferenceTreeAnnotator extends TreeAnnotator {

    // private final SlotManager slotManager;
    private final VariableAnnotator variableAnnotator;
    private final AnnotatedTypeFactory realTypeFactory;
    // private final InferrableChecker realChecker;

    // TODO: In the old InferenceAnnotatedTypeFactory there was a store between extends/implement identifier expressions
    // TODO: used for getTypeFromTypeTree, I believe this is superfluous (since they will already be placed in
    // TODO: AnnotatedTypeFactory) but I am unsure, therefore, we'll leave these todos and circle back
    // private Map<Tree, AnnotatedTypeMirror> extendsAndImplementsTypes = new HashMap<Tree, AnnotatedTypeMirror>();

    public InferenceTreeAnnotator(final InferenceAnnotatedTypeFactory atypeFactory,
                                  final InferrableChecker realChecker,
                                  final AnnotatedTypeFactory realAnnotatedTypeFactory,
                                  final VariableAnnotator variableAnnotator,
                                  final SlotManager slotManager) {
        super(atypeFactory);
        // this.slotManager = slotManager;
        this.variableAnnotator = variableAnnotator;
        this.realTypeFactory = realAnnotatedTypeFactory;
        // this.realChecker = realChecker;
    }

    @Override
    public Void visitAnnotatedType(AnnotatedTypeTree node, AnnotatedTypeMirror atm) {
        visit(node.getUnderlyingType(), atm);
        return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, AnnotatedTypeMirror type) {
        // this is necessary because to create refinement variables for type vars we need to insert
        // potential variables on them
        if (type.getKind() == TypeKind.TYPEVAR) {
            variableAnnotator.visit(type, node);
        }
        return null;
    }

    @Override
    public Void visitAssignment(AssignmentTree assignmentTree, AnnotatedTypeMirror type) {
        // this is necessary because to create refinement variables for type vars we need to insert
        // potential variables on them
        if (type.getKind() == TypeKind.TYPEVAR) {
            variableAnnotator.visit(type, assignmentTree);
        }
        return null;
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

        InferenceUtil.testArgument(classType instanceof AnnotatedDeclaredType,
                "Unexpected type for ClassTree ( " + classTree + " ) AnnotatedTypeMirror ( " + classType + " ) ");

        // For anonymous classes, we do not create additional variables, as they
        // were already handled by the visitNewClass. This would otherwise result
        // in new variables for an extends clause, which then cannot be inserted.
        if (!InferenceUtil.isAnonymousClass(classTree)) {
            this.variableAnnotator.visit(classType, classTree);
        }

        return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, AnnotatedTypeMirror identifierType) {
        if (identifierType instanceof AnnotatedTypeVariable) {
            // note, variableAnnotator should already have a type for this tree at this point
            variableAnnotator.visit(identifierType,node);
        } else {
            TreePath path = atypeFactory.getPath(node);
            if (path != null) {
                final TreePath parentPath = path.getParentPath();
                final Tree parentNode = parentPath.getLeaf();

                if (parentNode.getKind() == Kind.METHOD_INVOCATION) {

                    if (((MethodInvocationTree) parentNode).getTypeArguments().contains(node)) {
                        // Note: This can happen when the explicit type argument to a method is
                        // a type without type parameters.  For types with type parameters, the node
                        // is a parameterized type and is handled appropriately
                        // See Test: GenericMethodCall (compare the two cases where a type argument is expressly
                        // provide)
                        variableAnnotator.visit(identifierType, node);
                    }
                } else if (parentNode.getKind() == Kind.ANNOTATED_TYPE) {

                    // This case can indicate the identifier is wrapped in an annotation tree
                    final Tree grandParent = parentPath.getParentPath().getLeaf();
                    if (grandParent.getKind() == Kind.METHOD_INVOCATION) {
                        if (((MethodInvocationTree) grandParent).getTypeArguments().contains(parentNode)) {
                            variableAnnotator.visit(identifierType, node);
                        }
                    }

                }
            }
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

        InferenceUtil.testArgument(atm instanceof AnnotatedTypeVariable,
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

        InferenceUtil.testArgument(atm instanceof AnnotatedExecutableType,
                "Unexpected type for MethodTree ( " + methodTree + " ) AnnotatedTypeMirror ( " + atm + " ) ");

        variableAnnotator.visit(atm, methodTree);

        return null;
    }

    /**
     * Adds variables to the methodTypeArguments
     * // TODO: Verify that return types for generic methods work correctly
     */
    @Override
    public Void visitMethodInvocation(final MethodInvocationTree methodInvocationTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitMethodInvocation(methodInvocationTree, atm);

        // inferTypeArguments sometimes passes annotatateImplicit(methodInvocationTree, atm)
        if (atm instanceof AnnotatedNoType) {
            return null;
        }

        annotateMethodTypeArgs(methodInvocationTree);

        return null;
    }


    private void annotateMethodTypeArgs(final MethodInvocationTree methodInvocationTree) {

        if (!methodInvocationTree.getTypeArguments().isEmpty()) {
            final ParameterizedExecutableType methodFromUse =
                    atypeFactory.methodFromUse(methodInvocationTree);

            annotateMethodTypeArguments(methodInvocationTree.getTypeArguments(), methodFromUse.typeArgs);
        } else {
            // TODO: annotate types if there are types but no trees, I think this will be taken care of by
            // TODO: InferenceTypeArgumentInference which is not yet implemented
        }
    }

    private void annotateMethodTypeArgs(final NewClassTree newClassTree) {

        if (!newClassTree.getTypeArguments().isEmpty()) {
            final ParameterizedExecutableType constructorFromUse =
                    atypeFactory.constructorFromUse(newClassTree);

            annotateMethodTypeArguments(newClassTree.getTypeArguments(), constructorFromUse.typeArgs);

        } else {
            // TODO: annotate types if there are types but no trees
            // TODO: InferenceTypeArgumentInference
        }
    }

    private void annotateMethodTypeArguments(final List<? extends Tree> typeArgTrees,
                                              final List<AnnotatedTypeMirror> typeArgs) {
        if (!typeArgTrees.isEmpty()) {

            if (typeArgs.size() != typeArgTrees.size()) {
                throw new BugInCF(
                    "Number of type argument trees differs from number of types!\n"
                 +  "Type arguments ( " + InferenceUtil.join(typeArgs) + " ) \n"
                 +  "Trees ( " + InferenceUtil.join(typeArgTrees) + " )"
                );
            }

            for (int i = 0; i < Math.min(typeArgs.size(), typeArgTrees.size()); i++) {
                variableAnnotator.visit(typeArgs.get(i), typeArgTrees.get(i));
            }
        }
    }

    @Override
    public Void visitNewClass(final NewClassTree newClassTree, final AnnotatedTypeMirror atm) {
        // Apply Implicits
        super.visitNewClass(newClassTree,  atm);

        // There used to be logic for finding the type based on implicit extends clause or
        // implements clauses for anonymous classes. This seems to work without it.
        // See the version control history if there are issues.
        variableAnnotator.visit(atm, newClassTree.getIdentifier());

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
        // TODO: Here is where we would decide what tree to use in getPath, probably we look up the
        // TODO: path to the original varTree and handle it appropriately

        variableAnnotator.visit(atm, varTree);

        final Element varElem = TreeUtils.elementFromDeclaration(varTree);

        // TODO: THIS AND THE VISIT BINARY COULD INSTEAD BE PUT AT THE TOP OF THE VISIT METHOD OF VariableAnnotator
        // TODO: AS SPECIAL CASES, THIS WOULD MEAN WE COULD LEAVE storeElementType and addPrimaryCombVar AS PRIVATE
        // This happens here, unlike all the other stores because then we would have to add this code
        // to every atm/varTree combination, thoughts?
        switch (varElem.getKind()) {
            case RESOURCE_VARIABLE:
            case ENUM_CONSTANT:
            case FIELD:
            case LOCAL_VARIABLE:
            case PARAMETER:
            case EXCEPTION_PARAMETER:
                variableAnnotator.storeElementType(varElem, atm);
                break;

            default:
                throw new BugInCF("Unexpected element of kind ( " + varElem.getKind() + " ) element ( " + varElem + " ) ");
        }
        return null;
    }

    @Override
    public Void visitNewArray(final NewArrayTree newArrayTree, final AnnotatedTypeMirror atm) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        InferenceUtil.testArgument(atm instanceof AnnotatedArrayType,
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

        if (atm.getKind() != TypeKind.BOOLEAN) {
            throw new BugInCF("Unexpected type kind for instanceOfTree = " + instanceOfTree
                                   + " atm=" + atm);
        }

        InferenceAnnotatedTypeFactory infTypeFactory = (InferenceAnnotatedTypeFactory) atypeFactory;
        AnnotatedPrimitiveType instanceOfType = (AnnotatedPrimitiveType) realTypeFactory.getAnnotatedType(instanceOfTree);
        atm.replaceAnnotations(instanceOfType.getAnnotations());

        ConstantToVariableAnnotator constantToVarAnnotator = infTypeFactory.getConstantToVariableAnnotator();
        constantToVarAnnotator.visit(atm);

        // atm is always boolean, get actual tested type
        final AnnotatedTypeMirror testedType = infTypeFactory.getAnnotatedType(instanceOfTree.getType());

        // Adding a varAnnot equal to the top of the qualifier hierarchy so the class on the right of
        // the instanceof will have annotations in both hierarchies.  Adding top means that when the
        // resultant dataflow most-specific happens the annotation will not actually contribute
        // any meaningful constraints (because everything is more specific than top).
        if (testedType.getAnnotationInHierarchy(infTypeFactory.getVarAnnot()) == null) {
            testedType.addAnnotations(realTypeFactory.getQualifierHierarchy().getTopAnnotations());
        }
        constantToVarAnnotator.visit(testedType);

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
     * (See Issue 9)
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
            // There currently is some case that we are missing that requires us to annotate these.
            return null;
        }

        variableAnnotator.visit(type, node);
        return null;
    }

    @Override
    public Void visitParameterizedType(final ParameterizedTypeTree param, final AnnotatedTypeMirror atm) {
        // Do NOT call super method.
        // To match TreeAnnotator, we do not apply implicits

        variableAnnotator.visit(atm, param);
        return null;
    }

}
