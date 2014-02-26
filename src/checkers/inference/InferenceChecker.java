package checkers.inference;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import checkers.basetype.BaseTypeChecker;
import checkers.basetype.BaseTypeVisitor;
import checkers.inference.quals.VarAnnot;
import checkers.quals.Unqualified;

public class InferenceChecker extends BaseTypeChecker {

    private static List<Class<? extends Annotation>> INFERENCE_ANNOTATIONS = Arrays.asList(VarAnnot.class, Unqualified.class);

    @Override
    public void initChecker() {
        InferenceMain.getInstance().initInference(this);
        // Needed for error messages and reporting.
        super.initChecker();
        // Overrides visitor created by initChecker
        this.visitor = InferenceMain.getInstance().getVisitor();
    }

    public static List<Class<? extends Annotation>> getInferenceAnnotations() {
       return INFERENCE_ANNOTATIONS;
    }

    /**
     * Called during super.initChecker(). We want it to do nothing.
     */
    @Override
    protected BaseTypeVisitor<?> createSourceVisitor() {
        return null;
    }
}
