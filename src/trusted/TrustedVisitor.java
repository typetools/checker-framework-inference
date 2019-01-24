package trusted;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

public class TrustedVisitor extends InferenceVisitor<TrustedChecker, BaseAnnotatedTypeFactory> {

    public TrustedVisitor(
            TrustedChecker checker,
            InferenceChecker ichecker,
            BaseAnnotatedTypeFactory factory,
            boolean infer) {
        super(checker, ichecker, factory, infer);
    }
}
