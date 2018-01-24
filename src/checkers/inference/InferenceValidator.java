package checkers.inference;


import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeValidator;
import org.checkerframework.framework.type.AnnotatedTypeFactory;

/**
 * A visitor to validate the types in a tree.
 */
public class InferenceValidator extends BaseTypeValidator {

    protected boolean infer;

    public InferenceValidator(BaseTypeChecker checker,
            InferenceVisitor<?, ?> visitor,
            AnnotatedTypeFactory atypeFactory) {
        super(checker, visitor, atypeFactory);
    }

    public void setInfer(boolean infer) {
        this.infer = infer;
    }
}
