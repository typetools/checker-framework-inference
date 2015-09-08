package checkers.inference.util;

import checkers.inference.SlotManager;
import checkers.inference.model.VariableSlot;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;

import javax.lang.model.element.AnnotationMirror;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;

import static checkers.inference.InferenceQualifierHierarchy.isUnqualified;

/**
 * Adds VarAnnot to all locations in type that already have an annotation
 * in the "real" qualifier hierarchy.  Adds equality annotations between the
 * VarAnnot and the real qualifier.
 */
public class ConstantToVariableAnnotator extends AnnotatedTypeScanner<Void, Void> {

    //a mapping of a "real qualifiers" and VarAnnots whose only solution is EXACTLY the realQualifier
    private final Map<Class<? extends Annotation>, VariableSlot> constantToVarAnnot;
    private final AnnotationMirror unqualified;
    private final AnnotationMirror varAnnot;
    private final SlotManager slotManager;

    public ConstantToVariableAnnotator(AnnotationMirror unqualified, AnnotationMirror varAnnot, SlotManager slotManager,
                                       Map<Class<? extends Annotation>, VariableSlot> constantToVarAnnot) {
        this.unqualified = unqualified;
        this.varAnnot = varAnnot;
        this.slotManager = slotManager;
        this.constantToVarAnnot = constantToVarAnnot;
    }

    @Override
    public Void visitExecutable(AnnotatedExecutableType type, Void aVoid) {
        scan(type.getReturnType(), null);
        if (type.getReceiverType() != null) {
            scanAndReduce(type.getReceiverType(), null, null);
        }
        scanAndReduce(type.getParameterTypes(), null, null);
        scanAndReduce(type.getThrownTypes(), null, null);
        scanAndReduce(type.getTypeVariables(), null, null);
        return null;
    }

    @Override
    protected Void scan(AnnotatedTypeMirror type, Void aVoid) {

        if (!type.getAnnotations().isEmpty()) {
            addVariableAnnotation(type);
        }
        super.scan(type, null);
        return null;
    }

    /**
     * if type is not annotated in the VarAnnot qualifier hierarchy:
     *    Find the "Constant" varAnnot that corresponds to the "real qualifier on VarAnnot"
     *    add the VarAnnot to the definite type use location
     *
     * @param type A type annotated in the "real qualifier hierarch"
     */
    protected void addVariableAnnotation(final AnnotatedTypeMirror type) {
        if (type.getAnnotationInHierarchy(varAnnot) != null) {
            return;
        }

        AnnotationMirror realQualifier = type.getAnnotationInHierarchy(unqualified);
        if (isUnqualified(realQualifier)) {
            ErrorReporter.errorAbort("All types should have a real (not-unqualified) type qualifier) " + type);
        }

        for (Entry<Class<? extends Annotation>, VariableSlot> qualToVarAnnot : constantToVarAnnot.entrySet()) {

            if (AnnotationUtils.areSameByClass(realQualifier, qualToVarAnnot.getKey())) {
                type.replaceAnnotation(slotManager.getAnnotation(qualToVarAnnot.getValue()));
                return;
            }
        }

        ErrorReporter.errorAbort("Could not find VarAnnot for real qualifier: " + realQualifier + " type =" + type);
    }
}