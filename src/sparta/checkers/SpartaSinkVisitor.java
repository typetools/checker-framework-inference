package sparta.checkers;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;

public class SpartaSinkVisitor extends InferenceVisitor<SpartaSinkChecker, BaseAnnotatedTypeFactory> {
    public SpartaSinkVisitor(SpartaSinkChecker checker, InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);
    }
    //We used to use this class to ensure that expressions used in conditionals
    //had sink CONDITIONAL. Since we removed conditional, this is no longer needed.
    //We will need to do something similar in the future for implicit information flow.
}
