package checkers.inference;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

public class InferenceChecker extends BaseTypeChecker {

    @Override
    public void initChecker() {
        InferenceMain.getInstance().recordInferenceCheckerInstance(this);
        // Needed for error messages and reporting.
        super.initChecker();
        // Overrides visitor created by initChecker
        this.visitor = InferenceMain.getInstance().getVisitor();
    }

    /**
     * Called during super.initChecker(). We want it to do nothing.
     */
    @Override
    protected BaseTypeVisitor<?> createSourceVisitor() {
        return null;
    }
}
