package checkers.inference.model;

import annotations.io.ASTIndex.ASTRecord;

/**
 * Slots represent logical variables over which Constraints are generated.
 *
 * Each slot is attached to a code location that can hold an annotation OR has an intrinsic meaning
 * within type-systems.  E.g: an int literal is always NonNull but can't hold an annotation, nonetheless, we
 * generate a ConstantSlot representing the literal.
 *
 */
public abstract class Slot {

    /**
     * Used to locate this Slot in source code.  ASTRecords are written to Jaif files along
     * with the Annotation determined for this slot by the Solver.
     */
    private ASTRecord astRecord;

    public Slot() { }

    public abstract Object serialize(Serializer serializer);

    public Slot(ASTRecord astRecord) {
        this.astRecord = astRecord;
    }

    public ASTRecord getASTRecord() {
        return astRecord;
    }

    public void setASTRecord(ASTRecord astRecord) {
        this.astRecord = astRecord;
    }
}
