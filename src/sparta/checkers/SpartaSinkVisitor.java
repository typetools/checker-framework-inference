package sparta.checkers;

import com.sun.source.tree.*;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;

import java.util.Set;

public class SpartaSinkVisitor extends InferenceVisitor<BaseTypeChecker, BaseAnnotatedTypeFactory> {

    public SpartaSinkVisitor(BaseTypeChecker checker, InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);
    }

    private void ensureConditionalSink(ExpressionTree tree) {

        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(tree);
        this.mainIsSubtype(type, SimpleFlowAnnotatedTypeFactory.CONDITIONAL_SINK, "", tree);
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        ensureConditionalSink(node.getCondition());
        return super.visitConditionalExpression(node, p);
    }

    @Override
    public Void visitIf(IfTree node, Void p) {
        ensureConditionalSink(node.getCondition());
        return super.visitIf(node, p);
    }

    @Override
    public Void visitSwitch(SwitchTree node, Void p) {
        ensureConditionalSink(node.getExpression());
        return super.visitSwitch(node, p);
    }

    @Override
    public Void visitCase(CaseTree node, Void p) {
        ExpressionTree exprTree = node.getExpression();
        if (exprTree != null)
            ensureConditionalSink(exprTree);
        return super.visitCase(node, p);
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree node, Void p) {
        ensureConditionalSink(node.getCondition());
        return super.visitDoWhileLoop(node, p);
    }

    @Override
    public Void visitWhileLoop(WhileLoopTree node, Void p) {
        ensureConditionalSink(node.getCondition());
        return super.visitWhileLoop(node, p);
    }

    // Nothing needed for EnhancedForLoop, no boolean get's unboxed there.
    @Override
    public Void visitForLoop(ForLoopTree node, Void p) {
        if (node.getCondition() != null) {
            // Condition is null e.g. in "for (;;) {...}"
            ensureConditionalSink(node.getCondition());
        }

        return super.visitForLoop(node, p);
    }
}
