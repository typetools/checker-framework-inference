package sparta.checkers;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;

public class SpartaVisitor extends InferenceVisitor<BaseTypeChecker, BaseAnnotatedTypeFactory> {

    public SpartaVisitor(BaseTypeChecker checker, InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);
    }

}
