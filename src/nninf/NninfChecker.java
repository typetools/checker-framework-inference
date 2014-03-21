package nninf;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import nninf.quals.NonNull;
import nninf.quals.Nullable;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.qual.TypeQualifiers;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.Pair;

import checkers.inference.ConstraintManager;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.dataflow.InferenceAnalysis;

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
                    InferenceChecker checker,
                    GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
                    List<Pair<VariableElement, CFValue>> fieldValues,
                    SlotManager slotManager,
                    ConstraintManager constraintManager,
                    InferrableChecker realChecker) {

        return new InferenceAnalysis(checker, factory, fieldValues, slotManager, constraintManager, realChecker);
    }

    @Override
    public CFTransfer createInferenceTransferFunction(InferenceAnalysis analysis) {
        return new NninfTransfer(analysis);
    }

    @Override
    public boolean isConstant(AnnotatedTypeMirror typeMirror) {
        return (typeMirror instanceof AnnotatedPrimitiveType
                || typeMirror instanceof AnnotatedNullType);
    }

    @Override
    public boolean withCombineConstraints() {
        return false;
    }
}