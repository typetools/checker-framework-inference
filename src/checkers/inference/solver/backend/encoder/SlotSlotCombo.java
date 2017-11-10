package checkers.inference.solver.backend.encoder;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoder;

/**
 * Enum that models combination of slots so that encoders knows which encodeXXX() method should be
 * called. Only knowing Variable or Constant is enough in solver encoding. Because solver treats
 * all VariableSlots as having unknown value that is to be inferred and ConstantSlot as having a real
 * qualifier that no inference is needed.
 * @see ConstraintEncoderCoordinator#dispatch(BinaryConstraint, BinaryConstraintEncoder) for example
 */
public enum SlotSlotCombo {

    VARIABLE_VARIABLE(true, true),
    VARIABLE_CONSTANT(true, false),
    CONSTANT_VARIABLE(false, true),
    CONSTANT_CONSTANT(false, false);

    static final SlotSlotCombo[][] map = new SlotSlotCombo[2][2];

    static {
        for (SlotSlotCombo combo : SlotSlotCombo.values()) {
            map[index(combo.isFstVariableSlot)][index(combo.isSndVariableSlot)] = combo;
        }
    }

    private final boolean isFstVariableSlot;
    private final boolean isSndVariableSlot;

    SlotSlotCombo(boolean isFstVariableSlot, boolean isSndVariableSlot) {
        this.isFstVariableSlot = isFstVariableSlot;
        this.isSndVariableSlot = isSndVariableSlot;
    }

    public static SlotSlotCombo valueOf(BinaryConstraint binaryConstraint) {
        return valueOf(binaryConstraint.getFirst(), binaryConstraint.getSecond());
    }

    public static SlotSlotCombo valueOf(CombineConstraint combineConstraint) {
        return valueOf(combineConstraint.getTarget(), combineConstraint.getDeclared());
    }

    private static SlotSlotCombo valueOf(Slot fst, Slot snd) {
        return map[index(fst.isVariable())][index(snd.isVariable())];
    }

    private static int index(boolean value) {
        return value ? 1 : 0;
    }
}
