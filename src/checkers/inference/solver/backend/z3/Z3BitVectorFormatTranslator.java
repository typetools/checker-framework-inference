package checkers.inference.solver.backend.z3;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.backend.z3.encoder.Z3BitVectorEqualityConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorPreferenceConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorSubtypeConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorCombineConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorComparableConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorExistentialConstraintEncoder;
import checkers.inference.solver.backend.z3.encoder.Z3BitVectorInEqualityConstraintEncoder;
import checkers.inference.util.ConstraintVerifier;
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
import checkers.inference.solver.backend.FormatTranslator;
import checkers.inference.solver.frontend.Lattice;

public abstract class Z3BitVectorFormatTranslator extends FormatTranslator<BitVecExpr, BoolExpr, BitVecNum>{

    private Optimize solver;

    private Map<Integer, BitVecExpr> serializedSlots;

    protected Context context;

    protected final BoolExpr EMPTY_VALUE = null;

    // TODO Charles please add domain specific value here instead of null
    protected final BoolExpr CONSTRADICTORY_VALUE = null;

    protected final Z3BitVectorCodec z3BitVectorCodec;

    public Z3BitVectorFormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        super(lattice, verifier);
        z3BitVectorCodec = createZ3BitVectorCodec();
        serializedSlots = new HashMap<>();
    }

    // TODO Charles, can this be public? Must it stay in Z3BitVectorFormatTranslator?
    public boolean isSubtypeSubSet() {
        return true;
    }

    public final void initContext(Context context) {
        this.context = context;
    }

    public final void initSolver(Optimize solver) {
        this.solver = solver;
        postInit();
    }

    /**
     * Create a Z3BitVectorCodec responsibile for encoding/decoding a type qualifier.
     * Each type system must provide a specific Z3BitVectorCodec.
     * @return a z3BitVectorCodec
     */
    protected abstract Z3BitVectorCodec createZ3BitVectorCodec();

    public Z3BitVectorCodec getZ3BitVectorCodec() {
        return z3BitVectorCodec;
    }

    public BoolExpr getEmptyValue() {
        return EMPTY_VALUE;
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
    protected Z3BitVectorSubtypeConstraintEncoder createSubtypeConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorSubtypeConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorEqualityConstraintEncoder createEqualityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorEqualityConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorInEqualityConstraintEncoder createInequalityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorInEqualityConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorComparableConstraintEncoder createComparableConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorComparableConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorCombineConstraintEncoder createCombineConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorCombineConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorPreferenceConstraintEncoder createPreferenceConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorPreferenceConstraintEncoder(lattice, verifier, context, solver, this);
    }

    @Override
    protected Z3BitVectorExistentialConstraintEncoder createExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier) {
        return new Z3BitVectorExistentialConstraintEncoder(lattice, verifier, context, solver, this);
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
