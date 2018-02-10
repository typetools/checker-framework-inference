package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Z3BitVectorSubtypeConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements SubtypeConstraintEncoder<BoolExpr> {

    public Z3BitVectorSubtypeConstraintEncoder(Lattice lattice, Context context,
            Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, context, z3BitVectorFormatTranslator);
    }

    protected boolean isSubtypeSubset() {
        return true;
    }

    protected BoolExpr encode(Slot subtype, Slot supertype) {
        BitVecExpr subtypeBv = subtype.serialize(z3BitVectorFormatTranslator);
        BitVecExpr supertypeBv = supertype.serialize(z3BitVectorFormatTranslator);
        BitVecExpr subSet;
        BitVecExpr superSet;

        if (isSubtypeSubset()) {
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
    public BoolExpr encodeVariable_Variable(VariableSlot subtype, VariableSlot supertype) {
       return encode(subtype, supertype);
    }

    @Override
    public BoolExpr encodeVariable_Constant(VariableSlot subtype, ConstantSlot supertype) {
        return encode(subtype, supertype);
    }

    @Override
    public BoolExpr encodeConstant_Variable(ConstantSlot subtype, VariableSlot supertype) {
        return encode(subtype, supertype);
    }
}
