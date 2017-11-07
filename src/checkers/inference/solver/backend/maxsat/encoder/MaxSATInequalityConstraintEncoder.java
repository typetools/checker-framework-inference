package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.InequalityConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MathUtils;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * Created by mier on 06/11/17.
 */
public class MaxSATInequalityConstraintEncoder extends MaxSATAbstractBinaryConstraintEncoder implements InequalityConstraintEncoder<VecInt[]> {

    public MaxSATInequalityConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, typeToInt);
    }

    @Override
    public VecInt[] encodeVariable_Variable(VariableSlot fst, VariableSlot snd) {
        // a <=> !b which is the same as (!a v !b) & (b v a)
        VecInt[] result = new VecInt[lattice.numTypes * 2];
        int i = 0;
        for (AnnotationMirror type : lattice.allTypes) {
            if (lattice.allTypes.contains(type)) {
                result[i] = VectorUtils.asVec(
                        -MathUtils.mapIdToMatrixEntry(fst.getId(), typeToInt.get(type), lattice),
                        -MathUtils.mapIdToMatrixEntry(snd.getId(), typeToInt.get(type), lattice));
                result[i + 1] = VectorUtils.asVec(
                        MathUtils.mapIdToMatrixEntry(snd.getId(), typeToInt.get(type), lattice),
                        MathUtils.mapIdToMatrixEntry(fst.getId(), typeToInt.get(type), lattice));
                i = i + 2;
            }
        }
        return result;
    }

    @Override
    public VecInt[] encodeVariable_Constant(VariableSlot fst, ConstantSlot snd) {
        return encodeConstant_Variable(snd, fst);
    }

    @Override
    public VecInt[] encodeConstant_Variable(ConstantSlot fst, VariableSlot snd) {
        if (lattice.allTypes.contains(fst.getValue())) {
            return VectorUtils.asVecArray(
                    -MathUtils.mapIdToMatrixEntry(snd.getId(), typeToInt.get(fst.getValue()), lattice));
        } else {
            return emptyValue;
        }
    }

    @Override
    public VecInt[] encodeConstant_Constant(ConstantSlot fst, ConstantSlot snd) {
        return verifier.areInEqual(fst, snd) ? emptyValue : contradictoryValue;
    }
}
