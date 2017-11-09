package checkers.inference.solver.backend.z3.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;

/**
 * Created by mier on 07/11/17.
 */
public class Z3BitVectorSubtypeConstraintEncoder extends Z3BitVectorAbstractConstraintEncoder implements SubtypeConstraintEncoder<BoolExpr> {

    public Z3BitVectorSubtypeConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Context context, Optimize solver, Z3BitVectorFormatTranslator z3BitVectorFormatTranslator) {
        super(lattice, verifier, context, solver, z3BitVectorFormatTranslator);
    }

    @Override
    public BoolExpr encodeVariable_Variable(VariableSlot subtype, VariableSlot supertype) {
        BitVecExpr subtypeBv = z3BitVectorFormatTranslator.serializeVarSlot(subtype);
        BitVecExpr supertypeBv = z3BitVectorFormatTranslator.serializeVarSlot(supertype);
        BitVecExpr subSet;
        BitVecExpr superSet;

        if (z3BitVectorFormatTranslator.isSubtypeSubSet()) {
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
    public BoolExpr encodeVariable_Constant(VariableSlot subtype, ConstantSlot supertype) {
        BitVecExpr subtypeBv = z3BitVectorFormatTranslator.serializeVarSlot(subtype);
        BitVecExpr supertypeBv = z3BitVectorFormatTranslator.serializeConstantSlot(supertype);
        BitVecExpr subSet;
        BitVecExpr superSet;

        if (z3BitVectorFormatTranslator.isSubtypeSubSet()) {
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
    public BoolExpr encodeConstant_Variable(ConstantSlot subtype, VariableSlot supertype) {
        BitVecExpr subtypeBv = z3BitVectorFormatTranslator.serializeConstantSlot(subtype);
        BitVecExpr supertypeBv = z3BitVectorFormatTranslator.serializeVarSlot(supertype);
        BitVecExpr subSet;
        BitVecExpr superSet;

        if (z3BitVectorFormatTranslator.isSubtypeSubSet()) {
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
    public BoolExpr encodeConstant_Constant(ConstantSlot subtype, ConstantSlot supertype) {
        return verifier.isSubtype(subtype, supertype) ? emptyValue : contradictoryValue;
    }
}
