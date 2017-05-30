package checkers.inference.util;

import checkers.inference.SlotManager;
import checkers.inference.VariableAnnotator;
import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.ConstantSlot;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.javacutil.ErrorReporter;

import javax.lang.model.element.AnnotationMirror;

import static checkers.inference.InferenceQualifierHierarchy.isUnqualified;

/**
 * Adds VarAnnot to all locations in type that already have an annotation
 * in the "real" qualifier hierarchy.  Adds equality annotations between the
 * VarAnnot and the real qualifier.
 */
public class ConstantToVariableAnnotator extends AnnotatedTypeScanner<Void, Void> {

    private final AnnotationMirror unqualified;
    private final AnnotationMirror varAnnot;
    private final VariableAnnotator variableAnnotator;
    private final SlotManager slotManager;

    public ConstantToVariableAnnotator(AnnotationMirror unqualified, AnnotationMirror varAnnot,
                                       VariableAnnotator variableAnnotator, SlotManager slotManager) {
        this.unqualified = unqualified;
        this.varAnnot = varAnnot;
        this.variableAnnotator = variableAnnotator;
        this.slotManager = slotManager;
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
            addVariablePrimaryAnnotation(type);
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
    protected void addVariablePrimaryAnnotation(final AnnotatedTypeMirror type) {
        if (type.getAnnotationInHierarchy(varAnnot) != null) {
            return;
        }

        AnnotationMirror realQualifier = type.getAnnotationInHierarchy(unqualified);
        if (isUnqualified(realQualifier)) {
            ErrorReporter.errorAbort("All types should have a real (not-unqualified) type qualifier) " + type);
        }

        ConstantSlot varSlot = variableAnnotator.createConstant(realQualifier);
        type.replaceAnnotation(slotManager.getAnnotation(varSlot));
//
//        for (Entry<Class<? extends Annotation>, VariableSlot> qualToVarAnnot : constantToVarAnnot.entrySet()) {
//
//            if (AnnotationUtils.areSameByClass(realQualifier, qualToVarAnnot.getKey())) {
//                type.replaceAnnotation(slotManager.getAnnotation(qualToVarAnnot.getValue()));
//                return;
//            }
//        }
//
//        ErrorReporter.errorAbort("Could not find VarAnnot for real qualifier: " + realQualifier + " type =" + type);
    }

    public ConstantSlot createConstantSlot(final AnnotationMirror realQualifier) {
        ConstantSlot varSlot = variableAnnotator.createConstant(realQualifier);
        return varSlot;
    }
}