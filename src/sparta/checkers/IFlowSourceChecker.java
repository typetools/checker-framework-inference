package sparta.checkers;

import checkers.inference.BaseInferrableChecker;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.qual.StubFiles;

/**
 * Checker for inferring @Source annotations for SPARTA.
 *
 * <p>Only standard subtyping rules are needed so no methods are overridden.
 */
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
