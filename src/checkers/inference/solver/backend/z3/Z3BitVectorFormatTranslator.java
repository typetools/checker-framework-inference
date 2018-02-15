package checkers.inference.solver.backend.z3;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.backend.AbstractFormatTranslator;
import checkers.inference.solver.backend.encoder.ConstraintEncoderFactory;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorConstraintEncoderFactory;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.frontend.Lattice;

public abstract class Z3BitVectorFormatTranslator extends AbstractFormatTranslator<BitVecExpr, BoolExpr, BitVecNum> {

    private Optimize solver;

    private Map<Integer, BitVecExpr> serializedSlots;

    protected Context context;

    protected final Z3BitVectorCodec z3BitVectorCodec;

    public Z3BitVectorFormatTranslator(Lattice lattice) {
        super(lattice);
        z3BitVectorCodec = createZ3BitVectorCodec();
        serializedSlots = new HashMap<>();
    }

    public final void initContext(Context context) {
        this.context = context;
        finishInitializingEncoders();
    }

    public final void initSolver(Optimize solver) {
        this.solver = solver;
    }

    /**
     * Create a Z3BitVectorCodec responsible for encoding/decoding a type qualifier.
     * Each type system must provide a specific Z3BitVectorCodec.
     * @return a z3BitVectorCodec
     */
    protected abstract Z3BitVectorCodec createZ3BitVectorCodec();

    public Z3BitVectorCodec getZ3BitVectorCodec() {
        return z3BitVectorCodec;
    }

    /**
     * Add a soft constraint to underlying solver.
     * @param constraint the soft constraint
     * @param weight the weight of this soft constraint
     * @param group the group of this soft constraint
     */
    protected final void addSoftConstraint(BoolExpr constraint, int weight, String group) {
        solver.AssertSoft(constraint, weight, group);
    }

    public BitVecExpr serializeVarSlot(VariableSlot slot) {
        int slotId = slot.getId();

        if (serializedSlots.containsKey(slotId)) {
            return serializedSlots.get(slotId);
        }

        BitVecExpr bitVector = context.mkBVConst(String.valueOf(slot.getId()),
                z3BitVectorCodec.getFixedBitVectorSize());
        serializedSlots.put(slotId, bitVector);

        return bitVector;
    }

    public BitVecExpr serializeConstantSlot(ConstantSlot slot) {
        int slotId = slot.getId();

        if (serializedSlots.containsKey(slotId)) {
            return serializedSlots.get(slotId);
        }

        BigInteger numeralValue = z3BitVectorCodec.encodeConstantAM(slot.getValue());

        BitVecNum bitVecNum = context.mkBV(numeralValue.toString(), z3BitVectorCodec.getFixedBitVectorSize());
        serializedSlots.put(slotId, bitVecNum);

        return bitVecNum;
    }

    @Override
    protected ConstraintEncoderFactory<BoolExpr> createConstraintEncoderFactory() {
        return new Z3BitVectorConstraintEncoderFactory(lattice, context, this);
    }

    @Override
    public BitVecExpr serialize(VariableSlot slot) {
        return serializeVarSlot(slot);
    }

    @Override
    public BitVecExpr serialize(ConstantSlot slot) {
        return serializeConstantSlot(slot);
    }

    @Override
    public BitVecExpr serialize(ExistentialVariableSlot slot) {
        return serializeVarSlot(slot);
    }

    @Override
    public BitVecExpr serialize(RefinementVariableSlot slot) {
        return serializeVarSlot(slot);
    }

    @Override
    public BitVecExpr serialize(CombVariableSlot slot) {
        return serializeVarSlot(slot);
    }

    @Override
    public AnnotationMirror decodeSolution(BitVecNum solution, ProcessingEnvironment processingEnvironment) {
        return z3BitVectorCodec.decodeNumeralValue(solution.getBigInteger(), processingEnvironment);
    }
}
