package sparta.checkers;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.qual.TypeQualifiers;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;

import checkers.inference.BaseInferrableChecker;
import sparta.checkers.quals.PolySink;
import sparta.checkers.quals.Sink;

/**
 * Checker for inferring @Sink annotations for SPARTA.
 *
 * Only standard subtyping rules are needed so no methods are overridden.
 */
@TypeQualifiers({ Sink.class, PolySink.class})
@StubFiles("information_flow.astub")
public class IFlowSinkChecker extends BaseInferrableChecker {

    @Override
    public boolean isConstant(Tree node) {
        return (node instanceof LiteralTree);
    }

    @Override
    public BaseAnnotatedTypeFactory createRealTypeFactory() {
        return new SimpleFlowAnnotatedTypeFactory(this);
    }

    @Override
    public boolean shouldStoreConstantSlots() {
        return false;
    }
}