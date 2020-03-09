package nninf;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.TreeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.Trees;

public class NninfVisitor extends InferenceVisitor<NninfChecker, BaseAnnotatedTypeFactory> {

    private final TypeMirror stringType;

    public NninfVisitor(NninfChecker checker, InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);

        this.stringType = elements.getTypeElement("java.lang.String").asType();
    }

    /**
     * Ensure that the type is not of nullable.
     */
    private void checkForNullability(ExpressionTree tree, String errMsg) {
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(tree);
        mainIsNot(type, realChecker.NULLABLE, errMsg, tree);
    }

    @Override
    public Void visitBlock(BlockTree node, Void p) {
        if (infer) {
            Trees trees = Trees.instance(checker.getProcessingEnvironment());
            Scope scope = trees.getScope(getCurrentPath());
            System.out.println();
            Set<Element> variables = new HashSet<Element>(); // Variables accessible from within the block
            Set<Element> maps = new HashSet<Element>(); // Maps accessible within the block

            TypeElement mapElt =
                    checker.getProcessingEnvironment().getElementUtils().getTypeElement("java.util.Map");
            TypeMirror mapType = types.erasure(mapElt.asType());

            // Variables within the block.
            for (StatementTree tree : node.getStatements()) {
                if (tree instanceof VariableTree) {
                    Element elm = TreeUtils.elementFromDeclaration((VariableTree) tree);
                    variables.add(elm);
                    if (types.isSubtype(types.erasure(elm.asType()), mapType)) {
                        maps.add(elm);
                    }
                }
            }

            // Variables outside the block.
            while (scope.getEnclosingScope() != null) {
                for (Element elm : scope.getLocalElements()) {
                    if (elm.getKind() == ElementKind.FIELD
                            || elm.getKind() == ElementKind.LOCAL_VARIABLE
                            || elm.getKind() == ElementKind.PARAMETER) { // RESOURCE_VARIABLE?
                        variables.add(elm); // This may be unnecessary.
                        if (types.isSubtype(types.erasure(elm.asType()), mapType)) {
                            maps.add(elm);
                        }
                    }

                    // Get the fields
                    if (elm.getKind() == ElementKind.CLASS) {
                        for (Element field : elm.getEnclosedElements()) {
                            if (field.getKind() == ElementKind.FIELD) {
                                variables.add(field);
                                if (types.isSubtype(types.erasure(field.asType()), mapType)) {
                                    maps.add(field);
                                }
                            }
                        }
                    }
                }
                scope = scope.getEnclosingScope();
            }
            for (Element var : variables) {
                Element keyElement;
                TypeMirror type = var.asType();
                // Check for boxed types. Ex. int can be a key for Map<Integer,String>.
                if (type.getKind().isPrimitive()) {
                    PrimitiveType pType= (PrimitiveType) type;
                    keyElement = types.boxedClass(pType);
                } else {
                    keyElement = var;
                }
                for (Element map : maps) {
                    if (map.asType().getKind() == TypeKind.DECLARED) {
                        DeclaredType dType = (DeclaredType) map.asType();
                        List<? extends TypeMirror> list= dType.getTypeArguments();
                        if (list.size() > 0) {
                            if (types.isSubtype(keyElement.asType(), list.get(0))) {
                                // log possible KeyFor constraint.
                                // System.out.println(var + " is a possible @KeyFor " + map);
                            }
                        }
                    }

                }
            }
        }
        return super.visitBlock(node, p);
    }

    /**
     * Nninf does not use receiver annotations, forbid them.
     */
    @Override
    public Void visitMethod(MethodTree node, Void p) {
        // final VariableTree receiverParam = node.getReceiverParameter();

        // TODO: Talk to mike, this is a problem because calling getAnnotatedType in different locations leads
        // TODO: to Different variablePositions
        // TODO JB: Put this back in and disable receiver additions for Nninf
        /*if(receiverParam != null) { // TODO: CAN THIS BE NULL?  Only if this is a constructor?
            final AnnotatedTypeMirror typeMirror = atypeFactory.getAnnotatedType(receiverParam);

            if (!typeMirror.getAnnotations().isEmpty()) {
                checker.reportError(node, "receiver.annotations.forbidden");
            }
        }*/

        return super.visitMethod(node, p);
    }

    /**
     * Ignore method receiver annotations.
     */
    @Override
    protected void checkMethodInvocability(AnnotatedExecutableType method,
            MethodInvocationTree node) {
    }

    /**
     * Ignore constructor receiver annotations.
     */
    @Override
    protected void checkConstructorInvocation(AnnotatedDeclaredType dt,
            AnnotatedExecutableType constructor, NewClassTree src) {
    }

    /** Check for null dereferencing */
    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        // TODO JB: Talk to Werner/Mike about class A extends OtherClass.InnerClass
//        if( InferenceUtils.isInExtendsImplements( node, atypeFactory )) {
//            return null;
//        }


        // Note that the ordering is important! First receiver expression, then create field access, then inequality.
        super.visitMemberSelect(node, p);
        // TODO: How do I decide whether something is a field read or update?
        // We currently create an access and then a set constraint.
        if (!atypeFactory.isAnyEnclosingThisDeref(node)) {
            // TODO: determining whether something is "this" doesn't seem to work correctly,
            // as I still get constraints with LiteralThis.
            checkForNullability(node.getExpression(), "dereference.of.nullable");
        }
//        logFieldAccess(node);
        return null;
    }


    /** Class instantiation is always non-null.
     * TODO: resolve this automatically?
     */
    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        super.visitNewClass(node, p);
        checkForNullability(node, "newclass.null");
        return null;
    }


    /** Check for implicit {@code .iterator} call */
    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        super.visitEnhancedForLoop(node, p);
        checkForNullability(node.getExpression(), "dereference.of.nullable");
        return null;
    }

    /** Check for array dereferencing */
    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        super.visitArrayAccess(node, p);
        checkForNullability(node.getExpression(), "accessing.nullable");
        return null;
    }

    /** Check for thrown exception nullness */
    @Override
    public Void visitThrow(ThrowTree node, Void p) {
        super.visitThrow(node, p);
        checkForNullability(node.getExpression(), "throwing.nullable");
        return null;
    }

    /** Check for synchronizing locks */
    @Override
    public Void visitSynchronized(SynchronizedTree node, Void p) {
        super.visitSynchronized(node, p);
        checkForNullability(node.getExpression(), "locking.nullable");
        return null;
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        super.visitConditionalExpression(node, p);
        checkForNullability(node.getCondition(), "condition.nullable");
        return null;
    }

    @Override
    public Void visitIf(IfTree node, Void p) {
        super.visitIf(node, p);
        checkForNullability(node.getCondition(), "condition.nullable");
        return null;
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree node, Void p) {
        super.visitDoWhileLoop(node, p);
        checkForNullability(node.getCondition(), "condition.nullable");
        return null;
    }

    @Override
    public Void visitWhileLoop(WhileLoopTree node, Void p) {
        super.visitWhileLoop(node, p);
        checkForNullability(node.getCondition(), "condition.nullable");
        return null;
    }

    // Nothing needed for EnhancedForLoop, no boolean get's unboxed there.
    @Override
    public Void visitForLoop(ForLoopTree node, Void p) {
        super.visitForLoop(node, p);
        if (node.getCondition()!=null) {
            // Condition is null e.g. in "for (;;) {...}"
            checkForNullability(node.getCondition(), "condition.nullable");
        }
        return null;
    }

    /** Check for switch statements */
    @Override
    public Void visitSwitch(SwitchTree node, Void p) {
        super.visitSwitch(node, p);
        checkForNullability(node.getExpression(), "switching.nullable");
        return null;
    }

    /**
     * Unboxing case: primitive operations
     */
    @Override
    public Void visitBinary(BinaryTree node, Void p) {
        super.visitBinary(node, p);
        final ExpressionTree leftOp = node.getLeftOperand();
        final ExpressionTree rightOp = node.getRightOperand();

        if (isUnboxingOperation(node)) {
            checkForNullability(leftOp, "unboxing.of.nullable");
            checkForNullability(rightOp, "unboxing.of.nullable");
        }

        return null;
    }

    /** Unboxing case: primitive operation */
    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        super.visitUnary(node, p);
        checkForNullability(node.getExpression(), "unboxing.of.nullable");
        return null;
    }

    /** Unboxing case: primitive operation */
    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        super.visitCompoundAssignment(node, p);
        // ignore String concatenation
        if (!isString(node)) {
            checkForNullability(node.getVariable(), "unboxing.of.nullable");
            checkForNullability(node.getExpression(), "unboxing.of.nullable");
        }
        return null;
    }

    // TODO: Nullness visitor has a copy of this method, do we want to just
    // TODO: make it static/public or put it in a util?
    private boolean isPrimitive(Tree tree) {
        return TreeUtils.typeOf(tree).getKind().isPrimitive();
    }

    /** Unboxing case: casting to a primitive */
    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        super.visitTypeCast(node, p);
        if (isPrimitive(node) && !isPrimitive(node.getExpression()))
            checkForNullability(node.getExpression(), "unboxing.of.nullable");
        return null;
    }

    // TODO: These TWO are from NonNullInfVisitor but are no longer static
    /** @return true if binary operation could cause an unboxing operation */
    private final boolean isUnboxingOperation(BinaryTree tree) {
        if (tree.getKind() == Tree.Kind.EQUAL_TO
                || tree.getKind() == Tree.Kind.NOT_EQUAL_TO) {
            // it is valid to check equality between two reference types, even
            // if one (or both) of them is null
            return isPrimitive(tree.getLeftOperand()) != isPrimitive(tree
                    .getRightOperand());
        } else {
            // All BinaryTree's are of type String, a primitive type or the
            // reference type equivalent of a primitive type. Furthermore,
            // Strings don't have a primitive type, and therefore only
            // BinaryTrees that aren't String can cause unboxing.
            return !isString(tree);
        }
    }

    /**
     * @return true if the type of the tree is a super of String
     * */
    private final boolean isString(ExpressionTree tree) {
        TypeMirror type = TreeUtils.typeOf(tree);
        return types.isAssignable(stringType, type);
    }
}
