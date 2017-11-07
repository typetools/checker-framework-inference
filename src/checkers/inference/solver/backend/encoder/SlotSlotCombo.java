package checkers.inference.solver.backend.encoder;

import checkers.inference.model.BinaryConstraint;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoder;

import static checkers.inference.solver.backend.encoder.SlotKind.CONSTANT;
import static checkers.inference.solver.backend.encoder.SlotKind.VARIABLE;

/**
 * Created by mier on 06/11/17.
 */
public enum SlotSlotCombo {

    VARIABLE_VARIABLE(VARIABLE, VARIABLE),
    VARIABLE_CONSTANT(VARIABLE, CONSTANT),
    CONSTANT_VARIABLE(CONSTANT, VARIABLE),
    CONSTANT_CONSTANT(CONSTANT, CONSTANT);

    private static final SlotSlotCombo[][] comboMap;

    static {
        comboMap = new SlotSlotCombo[SlotKind.values().length][SlotKind.values().length];
        for (SlotSlotCombo slotCombo : SlotSlotCombo.values()) {
            comboMap[slotCombo.slot1Kind.ordinal()][slotCombo.slot2Kind.ordinal()] = slotCombo;
        }
    }

    private final SlotKind slot1Kind;
    private final SlotKind slot2Kind;

    SlotSlotCombo(SlotKind slot1Kind, SlotKind slot2Kind) {
        this.slot1Kind = slot1Kind;
        this.slot2Kind = slot2Kind;
    }

    public static SlotSlotCombo valueOf(BinaryConstraint binaryConstraint) {
        return valueOf(SlotKind.valueOf(binaryConstraint.getFirst()), SlotKind.valueOf(binaryConstraint.getSecond()));
    }

    public static SlotSlotCombo valueOf(CombineConstraint combineConstraint) {
        return valueOf(SlotKind.valueOf(combineConstraint.getTarget()), SlotKind.valueOf(combineConstraint.getDeclared()));
    }

    public static SlotSlotCombo valueOf(SlotKind slot1Kind, SlotKind slot2Kind) {
        return comboMap[slot1Kind.ordinal()][slot2Kind.ordinal()];
    }
}
