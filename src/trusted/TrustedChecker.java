package trusted;


import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import nninf.GameChecker;

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

import trusted.quals.Trusted;
import trusted.quals.Untrusted;
import checkers.inference.ConstraintManager;
import checkers.inference.InferenceChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.SlotManager;
import checkers.inference.dataflow.InferenceAnalysis;
import checkers.inference.dataflow.InferenceTransfer;

/**
 * 
 * The Trusted checker is a generic checker for expressing objects as "trusted" or not.
 * It should most likely be only used abstractly; specific subtypes with their own
 * qualifiers should be created to represent most categories of trusted (e.g. for SQL
 * or OS commands).
 * 
 */
@TypeQualifiers({ Trusted.class, Untrusted.class })
public class TrustedChecker extends GameChecker {
    public AnnotationMirror UNTRUSTED, TRUSTED;

    @Override
    public void initChecker() {
        super.initChecker();
        setAnnotations();
    }

    protected void setAnnotations() {
        final Elements elements = processingEnv.getElementUtils();
        UNTRUSTED = AnnotationUtils.fromClass(elements, Untrusted.class);
        TRUSTED   = AnnotationUtils.fromClass(elements, Trusted.class);
    }

    @Override
    public TrustedVisitor createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer)  {
        return new TrustedVisitor(this, ichecker, factory, infer);
    }

    @Override
    public TrustedAnnotatedTypeFactory createRealTypeFactory() {
        return new TrustedAnnotatedTypeFactory(this);
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
        return new InferenceTransfer(analysis);
    }

    @Override
    public boolean isConstant(AnnotatedTypeMirror typeMirror) {
        return (typeMirror instanceof AnnotatedPrimitiveType || typeMirror instanceof AnnotatedNullType);
    }

    @Override
    public boolean withCombineConstraints() {
        return false;
    }
}