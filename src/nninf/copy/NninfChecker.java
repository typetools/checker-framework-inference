package nninf.copy;

import java.util.List;

import javacutils.AnnotationUtils;
import javacutils.Pair;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import nninf.copy.quals.NonNull;
import nninf.copy.quals.Nullable;
import checkers.basetype.BaseAnnotatedTypeFactory;
import checkers.basetype.BaseTypeChecker;
import checkers.flow.CFAbstractAnalysis;
import checkers.flow.CFAnalysis;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;
import checkers.inference.InferenceChecker;
import checkers.inference.dataflow.InferenceAnalysis;
import checkers.quals.TypeQualifiers;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import checkers.types.GenericAnnotatedTypeFactory;

@TypeQualifiers({ NonNull.class, Nullable.class/*, UnknownKeyFor.class, KeyFor.class*/ })
public class NninfChecker extends GameChecker {
    public AnnotationMirror NULLABLE, NONNULL, UNKNOWNKEYFOR, KEYFOR;

    @Override
    public void initChecker() {
        final Elements elements = processingEnv.getElementUtils();
        NULLABLE = AnnotationUtils.fromClass(elements, Nullable.class);
        NONNULL  = AnnotationUtils.fromClass(elements, NonNull.class);
        // UNKNOWNKEYFOR = annoFactory.fromClass(UnknownKeyFor.class);
        // KEYFOR = annoFactory.fromClass(KeyFor.class);

        super.initChecker();
    }

    @Override
    public NninfVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer)  {
        return new NninfVisitor(this, ichecker, factory, infer);
    }

    @Override
    public NninfAnnotatedTypeFactory createRealTypeFactory() {
        return new NninfAnnotatedTypeFactory(this);
    }

    // TODO: Put in a base class
    @Override
    public CFAnalysis createInferenceAnalysis(
            BaseTypeChecker checker,
            GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
            List<Pair<VariableElement, CFValue>> fieldValues) {
        return new InferenceAnalysis<InferenceChecker>(checker, factory, fieldValues);
    }

    @Override
    public CFTransfer createInferenceTransferFunction(
            CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return new NninfTransfer(analysis);
    }

    @Override
    public boolean isConstant(AnnotatedTypeMirror typeMirror) {
        return (typeMirror instanceof AnnotatedPrimitiveType);
    }

    @Override
    public boolean withCombineConstraints() {
        return false;
    }
}