package checkers.inference.util.defaults;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.util.defaults.Default;
import org.checkerframework.framework.util.defaults.QualifierDefaults;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.model.Slot;
import checkers.inference.qual.VarAnnot;
import checkers.inference.util.ConstantToVariableAnnotator;

/**
 * Apply default qualifiers in inference mode.
 *
 * In inference mode, unchecked bytecode needs default qualifiers.
 * To build constraints, these default qualifiers should be VarAnnots.
 * The super class {@code QualifierDefaults} would determine real
 * qualifiers for each type use location, and this class would replace
 * those real qualifiers by equivalent VarAnnots, and apply these
 * VarAnnots as defaults to a given type only if this type has not been
 * annotated with a VarAnnot.
 *
 * @see org.checkerframework.framework.util.defaults.QualifierDefaults
 *
 */
public class InferenceQualifierDefaults extends QualifierDefaults {

    private final SlotManager slotManager;
    public InferenceQualifierDefaults(Elements elements, AnnotatedTypeFactory atypeFactory) {
        super(elements, atypeFactory);
        slotManager = InferenceMain.getInstance().getSlotManager();
    }

    @Override
    protected DefaultApplierElement createDefaultApplierElement(AnnotatedTypeFactory atypeFactory,
            Element annotationScope, AnnotatedTypeMirror type, boolean applyToTypeVar) {
        return new InferenceDefaultApplierElement(atypeFactory, annotationScope, type, applyToTypeVar);
    }

    public class InferenceDefaultApplierElement extends DefaultApplierElement {

        public InferenceDefaultApplierElement(AnnotatedTypeFactory atypeFactory, Element scope,
                AnnotatedTypeMirror type, boolean applyToTypeVar) {
            super(atypeFactory, scope, type, applyToTypeVar);
        }

        /**
         * Instead of applying the real qualifier stored
         * in the given {@code def}, replacing it with the
         * equivalent VarAnnot and apply the VarAnnot on
         * the applied type.
         */
        @Override
        public void applyDefault(Default def) {
            this.location = def.location;
            // We replace the real qualifier with equivalent varAnnot
            // here, instead of mutating the annotation stored in `def`.
            // The reason is mutating the annotation stored in `def`
            // also needs to adapt logic of initializing sets of default
            // qualifiers for checked and unchecked code, which needs
            // more code and makes this class complicated.
            // Since this is just two line duplication logic with super method
            // here, we decided to replace real qualifier here instead of
            // mutating the annotation in `def`.
            AnnotationMirror equivalentVarAnno = slotManager.createEquivalentVarAnno(def.anno);
            impl.visit(type, equivalentVarAnno);
        }

        @Override
        protected boolean shouldBeAnnotated(AnnotatedTypeMirror type, boolean applyToTypeVar) {
            return super.shouldBeAnnotated(type, applyToTypeVar) && !type.hasAnnotation(VarAnnot.class);
        }
    }

}
