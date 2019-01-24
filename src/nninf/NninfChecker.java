package nninf;

import checkers.inference.BaseInferrableChecker;
import checkers.inference.InferenceChecker;
import checkers.inference.dataflow.InferenceAnalysis;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import nninf.qual.NonNull;
import nninf.qual.Nullable;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.javacutil.AnnotationBuilder;

public class NninfChecker extends BaseInferrableChecker {
    public AnnotationMirror NULLABLE, NONNULL, UNKNOWNKEYFOR, KEYFOR;

    @Override
    public void initChecker() {
        final Elements elements = processingEnv.getElementUtils();
        NULLABLE = AnnotationBuilder.fromClass(elements, Nullable.class);
        NONNULL = AnnotationBuilder.fromClass(elements, NonNull.class);
        // UNKNOWNKEYFOR = annoFactory.fromClass(UnknownKeyFor.class);
        // KEYFOR = annoFactory.fromClass(KeyFor.class);

        super.initChecker();
    }

    @Override
    public NninfVisitor createVisitor(
            InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        return new NninfVisitor(this, ichecker, factory, infer);
    }

    @Override
    public NninfAnnotatedTypeFactory createRealTypeFactory() {
        return new NninfAnnotatedTypeFactory(this);
    }

    @Override
    public CFTransfer createInferenceTransferFunction(InferenceAnalysis analysis) {
        return new NninfTransfer(analysis);
    }
}
