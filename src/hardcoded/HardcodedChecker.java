package hardcoded;

import javax.lang.model.util.Elements;

import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import hardcoded.quals.MaybeHardcoded;
import hardcoded.quals.NotHardcoded;
import hardcoded.quals.PolyHardcoded;
import org.checkerframework.framework.qual.TypeQualifiers;
import org.checkerframework.javacutil.AnnotationUtils;

import ostrusted.quals.OsTrusted;
import ostrusted.quals.OsUntrusted;
import ostrusted.quals.PolyOsTrusted;
import secret.quals.NotSecret;
import secret.quals.PolySecret;
import secret.quals.PossiblySecret;
import trusted.TrustedChecker;

@TypeQualifiers({ NotHardcoded.class, MaybeHardcoded.class, PolyHardcoded.class })
public class HardcodedChecker extends TrustedChecker {

    @Override
    public boolean isConstant(Tree node) {
        return (node instanceof LiteralTree);
    }

    @Override
    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();      //TODO: Makes you think a utils is being returned

        UNTRUSTED = AnnotationUtils.fromClass(elements, MaybeHardcoded.class);
        TRUSTED   = AnnotationUtils.fromClass(elements, NotHardcoded.class);
    }
}