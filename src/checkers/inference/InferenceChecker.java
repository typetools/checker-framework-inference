package checkers.inference;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import checkers.basetype.BaseTypeChecker;
import checkers.inference.quals.VarAnnot;
import checkers.quals.Unqualified;

public class InferenceChecker extends BaseTypeChecker {

    private static List<Class<? extends Annotation>> INFERENCE_ANNOTATIONS = Arrays.asList(VarAnnot.class, Unqualified.class);

    @Override
    public void initChecker() {
        InferenceMain.getInstance().initInference(this);
        this.visitor = InferenceMain.getInstance().getVisitor();
    }

    public static List<Class<? extends Annotation>> getInferenceAnnotations() {
       return INFERENCE_ANNOTATIONS;
    }
}
