package checkers.inference.util;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.model.ConstraintManager;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AbstractViewpointAdapter;
import org.checkerframework.javacutil.ErrorReporter;

import javax.lang.model.element.AnnotationMirror;

public class InferenceViewpointAdapter extends AbstractViewpointAdapter {

    private final SlotManager slotManager = InferenceMain.getInstance().getSlotManager();
    private final ConstraintManager constraintManager = InferenceMain.getInstance().getConstraintManager();

    public InferenceViewpointAdapter(AnnotatedTypeFactory atypeFactory) {
        super(atypeFactory);
    }

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm) {
        final VariableSlot varSlot = slotManager.getVariableSlot(atm);
        if (varSlot == null && !InferenceMain.isHackMode()) {
            ErrorReporter.errorAbort(atm + " doesn't contain a slot");
        }

        return varSlot == null ? null : slotManager.getAnnotation(varSlot);
    }

    @Override
    protected AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation) {
        assert receiverAnnotation != null && declaredAnnotation != null;
        final Slot recvSlot = slotManager.getSlot(receiverAnnotation);
        final Slot declSlot = slotManager.getSlot(declaredAnnotation);
        final Slot combVariableSlot = slotManager.createCombVariableSlot(recvSlot, declSlot);
        constraintManager.addCombineConstraint(recvSlot, declSlot, combVariableSlot);
        return slotManager.getAnnotation(combVariableSlot);
    }
}
