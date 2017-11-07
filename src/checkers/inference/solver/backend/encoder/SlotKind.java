package checkers.inference.solver.backend.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import org.checkerframework.javacutil.ErrorReporter;

/**
 * Created by mier on 06/11/17.
 */
public enum SlotKind {

    VARIABLE(VariableSlot.class),
    CONSTANT(ConstantSlot.class);

    public final Class<? extends VariableSlot> slotClass;

    SlotKind(Class<? extends VariableSlot> slotClass) {
        this.slotClass = slotClass;
    }

    public static SlotKind valueOf(final Slot slot) {
        Class<? extends Slot> argClass = slot.getClass();
        for (SlotKind slotKind : SlotKind.values()) {
            if (argClass.equals(slotKind.slotClass)) {
                return slotKind;
            }
        }

        ErrorReporter.errorAbort("Unrecognized Slot: " + slot);
        return null;
    }
}
