package checkers.inference;

import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.quals.VarAnnot;
import checkers.inference.util.CopyUtil;
import org.checkerframework.framework.qual.Unqualified;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.util.AnnotationBuilder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import java.util.HashMap;
import java.util.Map;

import static checkers.inference.InferenceQualifierHierarchy.isUnqualified;


/**
 * Adds annotations to types that have come from bytecode.  Note, these types will
 * receive concrete "real" annotations from the real type factory AND a VarAnnot.
 *
 * The VarAnnot is so that we have an annotation in both hierarchies and so that
 * for cases like Verigames the shape of a gameboard won't differ depending on
 * whether a library was in bytecode or source code.  Note: I believe this is
 * much less of an issue these days since the game is more akin to
 * human aided automatic solving.
 */
public class BytecodeTypeAnnotator {

    private final SlotManager slotManager;
    private final ConstraintManager constraintManager;

    //see org.checkerframework.framework.qual.Unqualified
    private final AnnotationMirror unqualified;

    //see checkers.inference.quals.VarAnnot
    private final AnnotationMirror varAnnot;
    private final AnnotatedTypeFactory realTypeFactory;

    //Note: If memory issues arise we may want to limit the size of this or just start
    //using partial storage on disk
    private Map<Element, AnnotatedTypeMirror> elementToType = new HashMap<>();

    public BytecodeTypeAnnotator(AnnotatedTypeFactory realTypeFactory) {
        slotManager = InferenceMain.getInstance().getSlotManager();
        constraintManager = InferenceMain.getInstance().getConstraintManager();
        unqualified = new AnnotationBuilder(realTypeFactory.getProcessingEnv(), Unqualified.class).build();
        varAnnot = new AnnotationBuilder(realTypeFactory.getProcessingEnv(), VarAnnot.class).build();
        this.realTypeFactory = realTypeFactory;
    }

    /**
     * Get the type of element from the realTypeFactory.  Copy it's annotations to inferenceType.
     * Add a @VarAnnot to all defaultable locations in inferenceType and add an equality constraint
     * between it and the "real" annotations
     * @param element The bytecode declaration from which inferenceType was created
     * @param inferenceType The type of element.  inferenceType will be annotated by this method
     */
    public void annotate(final Element element, final AnnotatedTypeMirror inferenceType) {
        final AnnotatedTypeMirror previousType = elementToType.get(element);
        if (previousType != null) {
            CopyUtil.copyAnnotations(previousType, inferenceType);
        } else {

            final AnnotatedTypeMirror realType = realTypeFactory.getAnnotatedType(element);

            CopyUtil.copyAnnotations(realType, inferenceType);
            new BytecodeTypeScanner().visit(inferenceType);

            elementToType.put(element, inferenceType.deepCopy());
        }
    }

    /**
     * Adds VarAnnot to all locations in type that already have an annotation
     * in the "real" qualifier hierarchy.  Adds equality annotations between the
     * VarAnnot and the real qualifier.
     */
    class BytecodeTypeScanner extends AnnotatedTypeScanner<Void, Void> {

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

        protected void addVariableAnnotation(final AnnotatedTypeMirror type) {
            if (type.getAnnotationInHierarchy(varAnnot) != null) {
                return;
            }

            final VariableSlot variable = new VariableSlot(null, slotManager.nextId());
            slotManager.addVariable(variable);
            type.addAnnotation(slotManager.getAnnotation(variable));

            AnnotationMirror realQualifier = type.getAnnotationInHierarchy(unqualified);

            if (!isUnqualified(realQualifier)) {
                Slot constantSlot = slotManager.getSlot(realQualifier);
                constraintManager.add(new EqualityConstraint(variable, constantSlot));
            }
        }
    }

}
