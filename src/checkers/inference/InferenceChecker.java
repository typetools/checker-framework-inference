package checkers.inference;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.qual.Unqualified;

import checkers.inference.quals.VarAnnot;

public class InferenceChecker extends BaseTypeChecker {

    private static List<Class<? extends Annotation>> INFERENCE_ANNOTATIONS = Arrays.asList(VarAnnot.class, Unqualified.class);

    @Override
    public void initChecker() {
        InferenceMain.getInstance().recordInferenceCheckerInstance(this);
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
