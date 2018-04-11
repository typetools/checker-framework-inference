package checkers.inference;


import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeValidator;
import org.checkerframework.framework.type.AnnotatedTypeFactory;

/**
 * A visitor to validate the types in a tree.
 */
public class InferenceValidator extends BaseTypeValidator {

    /**
     * Indicates whether the validator is in inference mode or not.
     *
     * This field is intended to make implementations of subclasses easier.
     * Instead of querying the {@link InferenceVisitor#infer}, subclasses can
     * directly query this field to decide whether to generate constraints or
     * perform typechecking.
     */
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
