package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MathUtils;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.checkerframework.javacutil.AnnotationUtils;
import org.sat4j.core.Vec;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mier on 06/11/17.
 */
public class MaxSATSubtypeConstraintEncoder extends MaxSATAbstractBinaryConstraintEncoder implements SubtypeConstraintEncoder<VecInt[]>{

    public MaxSATSubtypeConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, typeToInt);
    }

    @Override
    public VecInt[] encodeVariable_Variable(VariableSlot subtype, VariableSlot supertype) {
        // if subtype is top, then supertype is top.
        // if supertype is bottom, then subtype is bottom.
        VecInt supertypeOfTop = VectorUtils.asVec(
                -MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.top), lattice),
                MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.top), lattice));
        VecInt subtypeOfBottom = VectorUtils.asVec(
                -MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.bottom), lattice),
                MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.bottom), lattice));

        List<VecInt> resultList = new ArrayList<VecInt>();
        for (AnnotationMirror type : lattice.allTypes) {
            // if we know subtype
            if (!AnnotationUtils.areSame(type, lattice.top)) {
                resultList.add(VectorUtils
                        .asVec(getMaybe(type, subtype, supertype, lattice.superType.get(type))));
            }

            // if we know supertype
            if (!AnnotationUtils.areSame(type, lattice.bottom)) {
                resultList.add(VectorUtils
                        .asVec(getMaybe(type, supertype, subtype, lattice.subType.get(type))));
            }
        }
        resultList.add(supertypeOfTop);
        resultList.add(subtypeOfBottom);
        VecInt[] result = resultList.toArray(new VecInt[resultList.size()]);
        return result;
    }

    @Override
    public VecInt[] encodeVariable_Constant(VariableSlot subtype, ConstantSlot supertype) {
        final Set<AnnotationMirror> mustNotBe = new HashSet<>();
        if (AnnotationUtils.areSame(supertype.getValue(), lattice.bottom)) {
            return VectorUtils.asVecArray(
                    MathUtils.mapIdToMatrixEntry(subtype.getId(), typeToInt.get(lattice.bottom), lattice));
        }

        if (lattice.superType.get(supertype.getValue()) != null) {
            mustNotBe.addAll(lattice.superType.get(supertype.getValue()));
        }
        if (lattice.incomparableType.keySet().contains(supertype.getValue())) {
            mustNotBe.addAll(lattice.incomparableType.get(supertype.getValue()));
        }
        return getMustNotBe(mustNotBe, subtype, supertype);
    }

    @Override
    public VecInt[] encodeConstant_Variable(ConstantSlot subtype, VariableSlot supertype) {
        final Set<AnnotationMirror> mustNotBe = new HashSet<>();
        if (AnnotationUtils.areSame(subtype.getValue(), lattice.top)) {
            return VectorUtils.asVecArray(
                    MathUtils.mapIdToMatrixEntry(supertype.getId(), typeToInt.get(lattice.top), lattice));
        }
        if (lattice.subType.get(subtype.getValue()) != null) {
            mustNotBe.addAll(lattice.subType.get(subtype.getValue()));
        }

        if (lattice.incomparableType.keySet().contains(subtype.getValue())) {
            mustNotBe.addAll(lattice.incomparableType.get(subtype.getValue()));
        }
        return getMustNotBe(mustNotBe, supertype, subtype);
    }

    @Override
    public VecInt[] encodeConstant_Constant(ConstantSlot subtype, ConstantSlot supertype) {
        return verifier.isSubtype(subtype, supertype) ? emptyValue : contradictoryValue;
    }
}
