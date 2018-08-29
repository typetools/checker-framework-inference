package checkers.inference.util;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;


/**
 * Adds VarAnnot to all locations in type that already have an annotation
 * in the "real" qualifier hierarchy.  Adds equality annotations between the
 * VarAnnot and the real qualifier.
 */
public class ConstantToVariableAnnotator extends AnnotatedTypeScanner<Void, Void> {

    private final AnnotationMirror realTop;
    private final AnnotationMirror varAnnot;
    private static final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();

    public ConstantToVariableAnnotator(AnnotationMirror realTop, AnnotationMirror varAnnot) {
        this.realTop = realTop;
        this.varAnnot = varAnnot;
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
        if (type.isAnnotatedInHierarchy(varAnnot)) {
            return;
        }

        AnnotationMirror realQualifier = type.getAnnotationInHierarchy(realTop);
        AnnotationMirror equivalentVarAnno = slotManager.createEquivalentVarAnno(realQualifier);
        type.addAnnotation(equivalentVarAnno);
        type.removeAnnotation(realQualifier);
//
//        for (Entry<Class<? extends Annotation>, VariableSlot> qualToVarAnnot : constantToVarAnnot.entrySet()) {
//
//            if (AnnotationUtils.areSameByClass(realQualifier, qualToVarAnnot.getKey())) {
//                type.replaceAnnotation(slotManager.getAnnotation(qualToVarAnnot.getValue()));
//                return;
//            }
//        }
//
//        throw new BugInCF("Could not find VarAnnot for real qualifier: " + realQualifier + " type =" + type);
    }
}
