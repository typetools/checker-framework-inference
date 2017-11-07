package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.binary.BinaryConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MathUtils;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.checkerframework.javacutil.AnnotationUtils;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by mier on 06/11/17.
 */
public abstract class MaxSATAbstractBinaryConstraintEncoder extends MaxSATAbstractConstraintEncoder implements BinaryConstraintEncoder<VecInt[]>{

    public MaxSATAbstractBinaryConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, typeToInt);
    }

    /**
     * for subtype constraint, if supertype is constant slot, then the subtype
     * cannot be the super type of supertype, same for subtype
     *
     * @param mustNotBe
     * @param vSlot
     * @param cSlot
     * @return
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

    /**
     *
     * @param type
     * @param knownType
     * @param unknownType
     * @param maybeSet
     * @return
     */
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
}
