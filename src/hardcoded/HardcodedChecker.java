package hardcoded;

import org.checkerframework.javacutil.AnnotationBuilder;

import javax.lang.model.util.Elements;

import trusted.TrustedChecker;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;

import hardcoded.qual.MaybeHardcoded;
import hardcoded.qual.NotHardcoded;

public class HardcodedChecker extends TrustedChecker {

    @Override
    public boolean isConstant(Tree node) {
        return (node instanceof LiteralTree);
    }

    @Override
    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();      //TODO: Makes you think a utils is being returned

        UNTRUSTED = AnnotationBuilder.fromClass(elements, MaybeHardcoded.class);
        TRUSTED   = AnnotationBuilder.fromClass(elements, NotHardcoded.class);
    }
}