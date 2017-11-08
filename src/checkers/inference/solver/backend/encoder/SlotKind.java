package checkers.inference.solver.backend.encoder;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import org.checkerframework.javacutil.ErrorReporter;

/**
 * Created by mier on 06/11/17.
 */
public enum SlotKind {

    VARIABLE,
    CONSTANT;

    public static SlotKind valueOf(final Slot slot) {
        Class<?> argClass = slot.getClass();
        if (ConstantSlot.class.equals(argClass)) {
            return CONSTANT;
        } else if (VariableSlot.class.equals(argClass) ||
                CombVariableSlot.class.equals(argClass) ||
                ExistentialVariableSlot.class.equals(argClass) ||
                        RefinementVariableSlot.class.equals(argClass)) {
            return VARIABLE;
        } else {
            ErrorReporter.errorAbort("Unrecognized Slot: " + slot);
            return null;
        }
    }
}
