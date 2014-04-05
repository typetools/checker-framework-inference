package sparta.checkers;


import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import org.checkerframework.framework.qual.TypeQualifiers;

import sparta.checkers.quals.Sink;
import checkers.inference.BaseInferrableChecker;

/**
 * Checker for inferring @Sink annotations for SPARTA.
 *
 * Only standard subtyping rules are needed so no methods are overridden.
 */
@TypeQualifiers({ Sink.class})
public class SpartaSinkChecker extends BaseInferrableChecker {

    @Override
    public boolean isConstant(Tree node) {
        return (node instanceof LiteralTree);
    }
}