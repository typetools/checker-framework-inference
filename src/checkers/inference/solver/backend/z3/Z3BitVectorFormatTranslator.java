package checkers.inference.solver.backend.z3;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.CombineConstraint;
import checkers.inference.model.ComparableConstraint;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.EqualityConstraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.InequalityConstraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.FormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.VariableCombos;

public abstract class Z3BitVectorFormatTranslator implements FormatTranslator<BitVecExpr, BoolExpr, BitVecNum>{

    private Optimize solver;

    private Map<Integer, BitVecExpr> serializedSlots;

    protected Context context;

    protected final BoolExpr EMPTY_VALUE = null;

    // TODO Charles please add domain specific value here instead of null
    protected final BoolExpr CONSTRADICTORY_VALUE = null;

    protected final Lattice lattice;

    protected final Z3BitVectorCodec z3BitVectorCodec;

    protected Z3SubtypeVariableCombos subtypeVariableCombos;

    protected Z3EqualityVariableCombos equalityVariableCombos;

    public Z3BitVectorFormatTranslator(Lattice lattice) {
        this.lattice = lattice;
        z3BitVectorCodec = createZ3BitVectorCodec();
        serializedSlots = new HashMap<>();
        subtypeVariableCombos = new Z3SubtypeVariableCombos(EMPTY_VALUE, CONSTRADICTORY_VALUE, lattice);
        equalityVariableCombos = new Z3EqualityVariableCombos(EMPTY_VALUE, CONSTRADICTORY_VALUE, lattice);
    }

    protected boolean isSubtypeSubSet() {
        return true;
    }

    public final void initContext(Context context) {
        this.context = context;
    }

    public final void initSolver(Optimize solver) {
        this.solver = solver;
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

    @Override
    public BoolExpr serialize(SubtypeConstraint constraint) {
        return subtypeVariableCombos.accept(constraint.getSubtype(), constraint.getSupertype(), constraint);
    }

    @Override
    public BoolExpr serialize(EqualityConstraint constraint) {
        return equalityVariableCombos.accept(constraint.getFirst(), constraint.getSecond(), constraint);
    }

    @Override
    public BoolExpr serialize(InequalityConstraint constraint) {
        // TODO Can be supported.
        return EMPTY_VALUE;
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
    public BoolExpr serialize(ExistentialConstraint constraint) {
        // Not supported.
        return EMPTY_VALUE;
    }

    @Override
    public BoolExpr serialize(ComparableConstraint comparableConstraint) {
        // Not supported.
        return EMPTY_VALUE;
    }

    @Override
    public BoolExpr serialize(CombineConstraint combineConstraint) {
        // Not supported.
        return EMPTY_VALUE;
    }

    @Override
    /**
     * Return an equality constriant between variable and constant goal.
     * The caller should add the serialized constraint with soft option.
     */
    public BoolExpr serialize(PreferenceConstraint preferenceConstraint) {
        VariableSlot variableSlot = preferenceConstraint.getVariable();
        ConstantSlot constantSlot = preferenceConstraint.getGoal();

        BitVecExpr varBV = serializeVarSlot(variableSlot);
        BitVecExpr constBV = serializeConstantSlot(constantSlot);

        return context.mkEq(varBV, constBV);
    }

    class Z3SubtypeVariableCombos extends VariableCombos<SubtypeConstraint, BoolExpr> {

        public Z3SubtypeVariableCombos(BoolExpr emptyValue, BoolExpr contradictoryValue, Lattice lattice) {
            super(emptyValue, contradictoryValue, lattice);
        }

        @Override
        protected BoolExpr variable_constant(VariableSlot subtypeSlot, ConstantSlot supertypeSlot, SubtypeConstraint constraint) {
            BitVecExpr subtypeBv = serializeVarSlot(subtypeSlot);
            BitVecExpr supertypeBv = serializeConstantSlot(supertypeSlot);
            BitVecExpr subSet;
            BitVecExpr superSet;
            
            if (isSubtypeSubSet()) {
                subSet = subtypeBv;
                superSet = supertypeBv;
            } else {
                superSet = subtypeBv;
                subSet = supertypeBv;
            }

            BoolExpr sub_intersect_super = context.mkEq(context.mkBVAND(subtypeBv, supertypeBv), subSet);
            BoolExpr sub_union_super = context.mkEq(context.mkBVOR(subtypeBv, supertypeBv), superSet);

            return context.mkAnd(sub_intersect_super, sub_union_super);
        }

        @Override
        protected BoolExpr variable_variable(VariableSlot subtypeSlot, VariableSlot supertypeSlot, SubtypeConstraint constraint) {
            BitVecExpr subtypeBv = serializeVarSlot(subtypeSlot);
            BitVecExpr supertypeBv = serializeVarSlot(supertypeSlot);
            BitVecExpr subSet;
            BitVecExpr superSet;
            
            if (isSubtypeSubSet()) {
                subSet = subtypeBv;
                superSet = supertypeBv;
            } else {
                superSet = subtypeBv;
                subSet = supertypeBv;
            }

            BoolExpr sub_intersect_super = context.mkEq(context.mkBVAND(subtypeBv, supertypeBv), subSet);
            BoolExpr sub_union_super = context.mkEq(context.mkBVOR(subtypeBv, supertypeBv), superSet);

            return context.mkAnd(sub_intersect_super, sub_union_super);
        }

        @Override
        protected BoolExpr constant_variable(ConstantSlot subtypeSlot, VariableSlot supertypeSlot, SubtypeConstraint constraint) {
            BitVecExpr subtypeBv = serializeConstantSlot(subtypeSlot);
            BitVecExpr supertypeBv = serializeVarSlot(supertypeSlot);
            BitVecExpr subSet;
            BitVecExpr superSet;
            
            if (isSubtypeSubSet()) {
                subSet = subtypeBv;
                superSet = supertypeBv;
            } else {
                superSet = subtypeBv;
                subSet = supertypeBv;
            }

            BoolExpr sub_intersect_super = context.mkEq(context.mkBVAND(subtypeBv, supertypeBv), subSet);
            BoolExpr sub_union_super = context.mkEq(context.mkBVOR(subtypeBv, supertypeBv), superSet);

            return context.mkAnd(sub_intersect_super, sub_union_super);
        }
    }

    class Z3EqualityVariableCombos extends VariableCombos<EqualityConstraint, BoolExpr> {

        public Z3EqualityVariableCombos(BoolExpr emptyValue, BoolExpr contradictoryValue, Lattice lattice) {
            super(emptyValue, contradictoryValue, lattice);
        }

        @Override
        protected BoolExpr constant_variable(ConstantSlot constantSlot, VariableSlot variableSlot, EqualityConstraint constraint) {
            BitVecExpr constBv = serializeConstantSlot(constantSlot);
            BitVecExpr varBv = serializeVarSlot(variableSlot);
            
            return context.mkEq(constBv, varBv);
        }

        @Override
        protected BoolExpr variable_variable(VariableSlot slot1, VariableSlot slot2, EqualityConstraint constraint) {
            BitVecExpr varBv1 = serializeVarSlot(slot1);
            BitVecExpr varBv2 = serializeVarSlot(slot2);
            
            return context.mkEq(varBv1, varBv2);
        }

        @Override
        protected BoolExpr variable_constant(VariableSlot variableSlot, ConstantSlot constantSlot, EqualityConstraint constraint) {
            BitVecExpr constBv = serializeConstantSlot(constantSlot);
            BitVecExpr varBv = serializeVarSlot(variableSlot);
            return context.mkEq(constBv, varBv);
        }

        @Override
        public BoolExpr accept(Slot slot1, Slot slot2, EqualityConstraint constraint) {
            return super.accept(slot1, slot2, constraint);
        }
    }

    @Override
    public AnnotationMirror decodeSolution(BitVecNum solution, ProcessingEnvironment processingEnvironment) {
        return z3BitVectorCodec.decodeNumeralValue(solution.getBigInteger(), processingEnvironment);
    }
}
