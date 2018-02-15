package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.SubtypeConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MathUtils;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import org.checkerframework.javacutil.AnnotationUtils;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MaxSATSubtypeConstraintEncoder extends MaxSATAbstractConstraintEncoder implements SubtypeConstraintEncoder<VecInt[]> {

    public MaxSATSubtypeConstraintEncoder(Lattice lattice, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, typeToInt);
    }

    /**
     * For subtype constraint, if supertype is constant slot, then the subtype
     * cannot be the super type of supertype, same for subtype
     */
    protected VecInt[] getMustNotBe(Set<AnnotationMirror> mustNotBe, VariableSlot vSlot, ConstantSlot cSlot) {

        List<Integer> resultList = new ArrayList<Integer>();

        for (AnnotationMirror sub : mustNotBe) {
            if (!AnnotationUtils.areSame(sub, cSlot.getValue())) {
                resultList.add(-MathUtils.mapIdToMatrixEntry(vSlot.getId(), typeToInt.get(sub), lattice));
            }
        }

        VecInt[] result = new VecInt[resultList.size()];
        if (resultList.size() > 0) {
            Iterator<Integer> iterator = resultList.iterator();
            for (int i = 0; i < result.length; i++) {
                result[i] = VectorUtils.asVec(iterator.next().intValue());
            }
            return result;
        }
        return emptyValue;
    }

    protected int[] getMaybe(AnnotationMirror type, VariableSlot knownType, VariableSlot unknownType,
                             Collection<AnnotationMirror> maybeSet) {
        int[] maybeArray = new int[maybeSet.size() + 1];
        int i = 1;
        maybeArray[0] = -MathUtils.mapIdToMatrixEntry(knownType.getId(), typeToInt.get(type), lattice);
        for (AnnotationMirror sup : maybeSet) {
            maybeArray[i] = MathUtils.mapIdToMatrixEntry(unknownType.getId(), typeToInt.get(sup), lattice);
            i++;
        }
        return maybeArray;
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
}
