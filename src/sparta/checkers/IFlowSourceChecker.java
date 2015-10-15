package sparta.checkers;


import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.qual.StubFiles;
import org.checkerframework.framework.qual.TypeQualifiers;

import sparta.checkers.quals.PolySource;
import sparta.checkers.quals.Source;
import checkers.inference.BaseInferrableChecker;

/**
 * Checker for inferring @Source annotations for SPARTA.
 *
 * Only standard subtyping rules are needed so no methods are overridden.
 */
@TypeQualifiers({ Source.class, PolySource.class })
@StubFiles("information_flow.astub")
public class IFlowSourceChecker extends BaseInferrableChecker {

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