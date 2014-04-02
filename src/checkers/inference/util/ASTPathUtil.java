package checkers.inference.util;

import static com.sun.source.tree.Tree.Kind.CLASS;
import static com.sun.source.tree.Tree.Kind.METHOD;

import java.util.ArrayList;
import java.util.List;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.javacutil.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import annotations.io.ASTIndex;
import annotations.io.ASTIndex.ASTRecord;
import annotations.io.ASTPath;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;

public class ASTPathUtil {

    protected static final Logger logger = LoggerFactory.getLogger(ASTPathUtil.class);

    /**
     * Look up an ASTRecord for a node.
     * @param typeFactory Type factory to look up tree path (and CompilationUnit)
     * @param node The node to get a record for
     * @return The ASTRecord for node
     */
    public static ASTRecord getASTRecordForNode(final AnnotatedTypeFactory typeFactory, Tree node) {
        final TreePath path = typeFactory.getPath(node);
        // TODO: Handle paths we need to create.
        if (path == null) {
            // This currently happens for paths that don't exist (like extends or implicit receiver)
            // And sometimes we are trying to look up a desugared tree (dataflow creates fake tree's for things like unary).
            return null;
        }

        // ASTIndex caches the lookups, so we don't.
        if (ASTIndex.indexOf(path.getCompilationUnit()).containsKey(node)) {
            ASTRecord record = ASTIndex.indexOf(path.getCompilationUnit()).get(node);
            if (record == null) {
                logger.warn("ASTIndex returned null for record: " + node);
            }
            return record;
        } else {
            logger.debug("Did not find ASTRecord for node: " + node);
            return null;
        }
    }

    /**
     * Gets an ASTPath to the given node.
     * @param typeFactory The typeFactory to use to get paths
     * @param node The node to get the ASTPath to
     * @throws RuntimeException if there is an unrecognized tree in the path
     * @return The ASTPath from the enclosing method or class to the node
     */
    @Deprecated
    private static ASTPath getASTPathToNode(final AnnotatedTypeFactory typeFactory, Tree node) {
        final TreePath path = typeFactory.getPath(node);
        if (path == null) {
            // println("InferenceUtils::getASTPathToNode: empty path for Tree: " + node)
            return null;
        }

        return getASTPathToNode(node, path);
    }

    /**
     * Helper method to get an ASTPath to the given node.
     * @param node The node to get the ASTPath to
     * @param path The TreePath to the node
     * @throws RuntimeException if there is an unrecognized tree in the path
     * @return The ASTPath from the enclosing method or class to the node
     */
    public static ASTPath getASTPathToNode(final Tree node, final TreePath path) {

        final TreePath parentPath = path.getParentPath();

        //TODO: Figure this out
        if(parentPath == null){
            return new ASTPath();
        }

        final Tree parentNode = parentPath.getLeaf();
        final Tree.Kind parentKind = parentNode.getKind();

        if(parentKind == METHOD || parentKind == CLASS) {
            return new ASTPath();
        }

        final ASTPath astPath = getASTPathToNode(parentNode, parentPath);

        String selector = null; //if selector is not filled in an exception is thrown
        int arg = -1;           //default argument

        if (parentNode instanceof AnnotatedTypeTree){
            if (node instanceof AnnotationTree) {
                selector = ASTPath.ANNOTATION;
                arg = ((AnnotatedTypeTree) parentNode).getAnnotations().indexOf(node);
            } else if(node instanceof ExpressionTree){
                selector = ASTPath.UNDERLYING_TYPE;
            }

        } else if(parentNode instanceof ArrayAccessTree) {
            final ArrayAccessTree parentArrayAccess = (ArrayAccessTree) parentNode;
            if (node.equals(parentArrayAccess.getExpression())) {
                selector = ASTPath.EXPRESSION;
            } else if(node.equals(parentArrayAccess.getIndex())) {
                selector = ASTPath.INDEX;
            }

        } else if(parentNode instanceof ArrayTypeTree) {
            selector = ASTPath.TYPE;

        } else if(parentNode instanceof AssertTree) {
            final AssertTree parentAssert = (AssertTree) parentNode;
            if(node.equals(parentAssert.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if(node.equals(parentAssert.getDetail())) {
                selector = ASTPath.DETAIL;
            }

        } else if(parentNode instanceof CompoundAssignmentTree) {
            final CompoundAssignmentTree parentCompAssign = (CompoundAssignmentTree) parentNode;
            if(node.equals(parentCompAssign.getVariable())) {
                selector = ASTPath.VARIABLE;
            } else if(node.equals(parentCompAssign.getExpression())) {
                selector = ASTPath.EXPRESSION;
            }

        } else if(parentNode instanceof AssignmentTree) {
            final AssignmentTree parentAssign = (AssignmentTree) parentNode;
            if(node.equals(parentAssign.getVariable())) {
                selector = ASTPath.VARIABLE;
            } else if(node.equals(parentAssign.getExpression())) {
                selector =  ASTPath.EXPRESSION;
            }

        } else if(parentNode instanceof BinaryTree) {
            final BinaryTree parentBinTree = (BinaryTree) parentNode;
            if(node.equals(parentBinTree.getLeftOperand())) {
                selector = ASTPath.LEFT_OPERAND;
            } else if(node.equals(parentBinTree.getRightOperand())) {
                selector = ASTPath.RIGHT_OPERAND;
            }

        } else if(parentNode instanceof BlockTree) {
            selector = ASTPath.STATEMENT;
            arg = ((BlockTree) parentNode).getStatements().indexOf(node);

        } else if(parentNode instanceof CaseTree) {
            if (node instanceof ExpressionTree) {
                selector = ASTPath.EXPRESSION;
            } else if(node instanceof StatementTree) {
                selector = ASTPath.STATEMENT;
                arg = ((CaseTree) parentNode).getStatements().indexOf(node);
            }

        } else if(parentNode instanceof CatchTree) {
            if (node instanceof VariableTree){
                selector = ASTPath.PARAMETER;
            } else if(node instanceof BlockTree) {
                selector = ASTPath.BLOCK;
            }

        } else if(parentNode instanceof ConditionalExpressionTree) {
            final ConditionalExpressionTree parentConditional = (ConditionalExpressionTree) parentNode;
            if (node.equals(parentConditional.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if (node.equals(parentConditional.getTrueExpression())) {
                selector = ASTPath.TRUE_EXPRESSION;
            } else if (node.equals(parentConditional.getFalseExpression())) {
                selector = ASTPath.FALSE_EXPRESSION;
            }

        } else if(parentNode instanceof DoWhileLoopTree) {
            final DoWhileLoopTree parentDoWhile = (DoWhileLoopTree) parentNode;
            if (node.equals(parentDoWhile.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if(node.equals(parentDoWhile.getStatement())) {
                selector = ASTPath.STATEMENT;
            }

        } else if (parentNode instanceof EnhancedForLoopTree) {
            final EnhancedForLoopTree parentEnhancedForLoop = (EnhancedForLoopTree) parentNode;
            if (node.equals(parentEnhancedForLoop.getVariable())) {
                selector = ASTPath.VARIABLE;
            } else if (node.equals(parentEnhancedForLoop.getExpression())) {
                selector = ASTPath.EXPRESSION;
            } else if (node.equals(parentEnhancedForLoop.getStatement())) {
                selector = ASTPath.STATEMENT;
            }

        } else if (parentNode instanceof ExpressionStatementTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof ForLoopTree) {
            final ForLoopTree parentForLoop = (ForLoopTree) parentNode;
            if (node.equals(parentForLoop.getStatement())) {
                selector = ASTPath.STATEMENT;
            } else if (node.equals(parentForLoop.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if (parentForLoop.getInitializer().contains(node)) {
                selector = ASTPath.INITIALIZER;
                arg = parentForLoop.getInitializer().indexOf(node);
            } else if (parentForLoop.getUpdate().contains(node)) {
                selector = ASTPath.UPDATE;
                arg = parentForLoop.getUpdate().indexOf(node);
            }

        } else if (parentNode instanceof IfTree) {
            final IfTree parentIf = (IfTree) parentNode;
            if (node.equals(parentIf.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if(node.equals(parentIf.getThenStatement())) {
                selector = ASTPath.THEN_STATEMENT;
            } else if(node.equals(parentIf.getElseStatement())) {
                selector = ASTPath.ELSE_STATEMENT;
            }

        } else if (parentNode instanceof InstanceOfTree) {
            final InstanceOfTree parentInstanceOf = (InstanceOfTree) parentNode;
            if (node.equals(parentInstanceOf.getExpression())) {
                selector = ASTPath.EXPRESSION;
            } else if (node.equals(parentInstanceOf.getType())) {
                selector = ASTPath.TYPE;
            }

        } else if (parentNode instanceof LabeledStatementTree) {
            selector = ASTPath.STATEMENT;

        } else if (parentNode instanceof LambdaExpressionTree) {
            final LambdaExpressionTree parentLambda = (LambdaExpressionTree) parentNode;
            if(node instanceof VariableTree) {
                selector = ASTPath.PARAMETER;
                arg = parentLambda.getParameters().indexOf(node);
            } else if(node.equals(parentLambda.getBody())) {
                selector = ASTPath.BODY;
            }

        } else if (parentNode instanceof MemberReferenceTree) {
            final MemberReferenceTree parentMemberRef = (MemberReferenceTree) parentNode;
            if (node.equals(parentMemberRef.getQualifierExpression())) {
                selector = ASTPath.QUALIFIER_EXPRESSION;
            } else if (node instanceof ExpressionTree) {
                selector = ASTPath.TYPE_ARGUMENT;
                arg = parentMemberRef.getTypeArguments().indexOf(node);
            }

        } else if (parentNode instanceof MemberSelectTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof MethodInvocationTree) {
            final MethodInvocationTree parentMethodInvoc = (MethodInvocationTree) parentNode;
            if (node.equals(parentMethodInvoc.getMethodSelect())) {
                selector = ASTPath.METHOD_SELECT;
            } else if(node instanceof ExpressionTree) {
                selector = ASTPath.ARGUMENT;
                arg = parentMethodInvoc.getArguments().indexOf(node);
            } else {
                selector = ASTPath.TYPE_ARGUMENT;
                arg = parentMethodInvoc.getTypeArguments().indexOf(node);
            }

        } else if (parentNode instanceof NewArrayTree) {
            final NewArrayTree parentNewArray = (NewArrayTree) parentNode;
            if (parentNewArray.getDimensions().contains(node)) {
                selector = ASTPath.DIMENSION;
                arg = parentNewArray.getDimensions().indexOf(node);
            } else if (parentNewArray.getInitializers() != null && parentNewArray.getInitializers().contains(node)) {
                selector = ASTPath.INITIALIZER;
                arg = parentNewArray.getInitializers().indexOf(node);
            } else {
                selector = ASTPath.TYPE;
            }

        } else if (parentNode instanceof NewClassTree) {
            final NewClassTree parentNewClass = (NewClassTree) parentNode;
            if (parentNewClass.getEnclosingExpression() != null && parentNewClass.getEnclosingExpression().equals(node)) {
                selector = ASTPath.ENCLOSING_EXPRESSION;
            } else if (parentNewClass.getIdentifier().equals(node)) {
                selector = ASTPath.IDENTIFIER;
            } else if (parentNewClass.getArguments().contains(node)) {
                selector = ASTPath.ARGUMENT;
                arg = parentNewClass.getArguments().indexOf(node);
            } else if (parentNewClass.getTypeArguments().contains(node)) {
                selector = ASTPath.TYPE_ARGUMENT;
                arg = parentNewClass.getTypeArguments().indexOf(node);
            } else {
                selector = ASTPath.CLASS_BODY;
            }

        } else if (parentNode instanceof ParameterizedTypeTree) {
            final ParameterizedTypeTree parentParameterizedType = (ParameterizedTypeTree) parentNode;
            if (node.equals(parentParameterizedType.getType())){
                selector = ASTPath.TYPE;
            } else {
                selector = ASTPath.TYPE_ARGUMENT;
                arg = parentParameterizedType.getTypeArguments().indexOf(node);
            }

        } else if (parentNode instanceof ParenthesizedTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof ReturnTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof SwitchTree) {
            if (node instanceof ExpressionTree) {
                selector = ASTPath.EXPRESSION;
            } else if(node instanceof CaseTree) {
                selector = ASTPath.CASE;
                arg = ((SwitchTree) parentNode).getCases().indexOf(node);
            }

        } else if (parentNode instanceof SynchronizedTree) {
            if (node instanceof ExpressionTree) {
                selector = ASTPath.EXPRESSION;
            } else if (node instanceof BlockTree) {
                selector = ASTPath.BLOCK;
            }
        } else if (parentNode instanceof ThrowTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof TryTree) {
            final TryTree parentTry = (TryTree) parentNode;
            if (node.equals(parentTry.getBlock())) {
                selector = ASTPath.BLOCK;
            } else if(node.equals(parentTry.getFinallyBlock())) {
                selector = ASTPath.FINALLY_BLOCK;
            } else {
                selector = ASTPath.CATCH;
                arg = parentTry.getCatches().indexOf(node);
            }

        } else if (parentNode instanceof TypeCastTree) {
            final TypeCastTree parentTypeCast = (TypeCastTree) parentNode;
            if (node.equals(parentTypeCast.getExpression())) {
                selector = ASTPath.EXPRESSION;
            } else if (node.equals(parentTypeCast.getType())) {
                selector = ASTPath.TYPE;
            }

        } else if (parentNode instanceof UnaryTree) {
            selector = ASTPath.EXPRESSION;

        } else if (parentNode instanceof UnionTypeTree) {
            selector = ASTPath.TYPE_ALTERNATIVE;
            arg = ((UnionTypeTree) parentNode).getTypeAlternatives().indexOf(node);

        } else if (parentNode instanceof VariableTree) {
            final VariableTree parentVariable = (VariableTree) parentNode;
            if (node.equals(parentVariable.getType())) {
                selector = ASTPath.TYPE;
            } else if (node.equals(parentVariable.getInitializer())) {
                selector = ASTPath.INITIALIZER;
            }

        } else if (parentNode instanceof WhileLoopTree) {
            final WhileLoopTree parentWhile = (WhileLoopTree) parentNode;
            if (node.equals(parentWhile.getCondition())) {
                selector = ASTPath.CONDITION;
            } else if (node.equals(parentWhile.getStatement())) {
                selector = ASTPath.STATEMENT;
            }

        } else if (parentNode instanceof WildcardTree) {
            selector = ASTPath.BOUND;

        } else {
            //failure contigent
            //TODO: this is COMPLETELY INCORRECT, figure these cases out
            selector = ASTPath.FINALLY_BLOCK; //JUST NEEDED A NON-NULL VALUE
        }

        if (selector == null) {
            ErrorReporter.errorAbort("Unrecognized tree for parentTree( " + parentPath.getLeaf() +
                                       ") tree ( " + node + ")");
        }

        astPath.add(new ASTPath.ASTEntry(parentKind, selector, arg));
        return astPath;
    }

    /**
     * Convert an ASTPath to a list of entries. Mostly redundant given the function above, except that
     * it works.
     */
    // TODO TR: Remove this and replace references when bug above resolved.
    private List<ASTPath.ASTEntry> astPathToList(final ASTPath path) {
        int i = 0;
        List<ASTPath.ASTEntry> entries = new ArrayList<>(path.size());

        while (i < path.size()) {
            entries.add(path.get(i));
            i += 1;
        }
        return entries;
    }

    /**
     * Gets an ASTPath and converts it to a string in the format that can be read by the AFU.
     * @param path The ASTPath to convert
     * @return A String containing all of the ASTEntries of the ASTPath formatted as the AFU will parse it.
     */
    public String convertASTPathToAFUFormat(final ASTPath path) {
        final List<String> entryStrings = new ArrayList<String>(path.size());
        for(final ASTPath.ASTEntry entry : astPathToList(path)){
            final String entryStr = entry.toString();
            final int index = entryStr.indexOf('.');
            final String treeName = entry.getTreeKind().asInterface().getSimpleName();
            final String start;
            switch (treeName) {
                case "BinaryTree":
                    start = "BINARY";
                    break;

                case "UnaryTree":
                    start = "UNARY";
                    break;

                case "CompoundAssignmentTree":
                    start = "COMPOUND_ASSIGNMENT";
                    break;

                default:
                    start = entryStr.substring(0, index);
            }

            entryStrings.add(capsToProperCase(start) + entryStr.substring(index));
        }

        return InferenceUtil.join(entryStrings);
    }

    private static String capitalize(final String str) {
        final StringBuilder sb = new StringBuilder(str.length());
        sb.append(Character.toUpperCase(str.charAt(0)));
        sb.append(str.substring(1));
        return sb.toString();
    }

    /**
     * Takes a string in ALL_CAPS and converts it to ProperCase. Necessary because the AFU requires
     * kinds in ASTPaths to be in proper case, but by default we get them in all caps.
     * @param str String in all caps
     * @return The same string with the initial letters of each word (at the beginning or following
     * an underscore) copitalized and the other letters lowercased
     */
    private static String capsToProperCase(final String str) {
        final StringBuilder sb = new StringBuilder();
        final String [] words = str.split("_");
        for (final String word : words) {
            sb.append(capitalize(word.toLowerCase()));
        }
        return sb.toString();
    }
}
