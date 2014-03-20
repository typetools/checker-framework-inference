package trusted;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;

public class TrustedVisitor extends InferenceVisitor<TrustedChecker, BaseAnnotatedTypeFactory> {

    public TrustedVisitor(TrustedChecker checker, InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);
    }
}
