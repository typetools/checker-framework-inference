package checkers.inference;

import checkers.basetype.BaseTypeChecker;

public class InferenceChecker extends BaseTypeChecker {

    @Override
    public void initChecker() {
        InferenceMain.getInstance().initInference(this);
        this.visitor = InferenceMain.getInstance().getVisitor();
    }
}
