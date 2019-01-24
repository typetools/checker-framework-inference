package trusted;

import com.sun.source.tree.BinaryTree;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.*;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.TreeUtils;

public class TrustedAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    public TrustedAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        postInit();
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(super.createTreeAnnotator(), new TrustedTreeAnnotator());
    }

    private class TrustedTreeAnnotator extends TreeAnnotator {
        public TrustedTreeAnnotator() {
            super(TrustedAnnotatedTypeFactory.this);
        }

        /**
         * Handles String concatenation; only @Trusted + @Trusted = @Trusted. Any other
         * concatenation results in @Untrusted.
         */
        @Override
        public Void visitBinary(BinaryTree tree, AnnotatedTypeMirror type) {
            if (TreeUtils.isStringConcatenation(tree)) {
                AnnotatedTypeMirror lExpr = getAnnotatedType(tree.getLeftOperand());
                AnnotatedTypeMirror rExpr = getAnnotatedType(tree.getRightOperand());

                final TrustedChecker trustedChecker = (TrustedChecker) checker;
                if (lExpr.hasAnnotation(trustedChecker.TRUSTED)
                        && rExpr.hasAnnotation(trustedChecker.TRUSTED)) {
                    type.replaceAnnotation(trustedChecker.TRUSTED);
                } else {
                    type.replaceAnnotation(trustedChecker.UNTRUSTED);
                }
            }
            return super.visitBinary(tree, type);
        }
    }
}
