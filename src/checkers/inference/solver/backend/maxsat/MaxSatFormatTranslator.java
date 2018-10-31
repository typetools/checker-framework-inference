package checkers.inference.solver.backend.maxsat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.backend.AbstractFormatTranslator;
import checkers.inference.solver.backend.encoder.ConstraintEncoderFactory;
import checkers.inference.solver.backend.maxsat.encoder.MaxSATConstraintEncoderFactory;
import org.checkerframework.javacutil.AnnotationUtils;
import org.sat4j.core.VecInt;

import checkers.inference.solver.frontend.Lattice;

/**
 * MaxSatFormatTranslator converts constraint into array of VecInt as clauses.
 *
 * @author jianchu
 *
 */

public class MaxSatFormatTranslator extends AbstractFormatTranslator<VecInt[], VecInt[], Integer> {

    /**
     * typeToInt maps each type qualifier to an unique integer value starts from
     * 0 on continuous basis.
     */
    protected final Map<AnnotationMirror, Integer> typeToInt;

    /**
     * intToType maps an integer value to each type qualifier, which is a
     * reversed map of typeToInt.
     */
    protected final Map<Integer, AnnotationMirror> intToType;

    public MaxSatFormatTranslator(Lattice lattice) {
        super(lattice);
        // Initialize mappings between type and int.
        Map<AnnotationMirror, Integer>typeToIntRes = AnnotationUtils.createAnnotationMap();
        Map<Integer, AnnotationMirror> intToTypeRes = new HashMap<Integer, AnnotationMirror>();

        int curInt = 0;
        for (AnnotationMirror type : lattice.allTypes) {
            typeToIntRes.put(type, curInt);
            intToTypeRes.put(curInt, type);
            curInt ++;
        }

        typeToInt = Collections.unmodifiableMap(typeToIntRes);
        intToType = Collections.unmodifiableMap(intToTypeRes);
        finishInitializingEncoders();
    }

    @Override
    protected ConstraintEncoderFactory<VecInt[]> createConstraintEncoderFactory() {
        return new MaxSATConstraintEncoderFactory(lattice, typeToInt, this);
    }

    /**
     * generate well form clauses such that there is one and only one beta value
     * can be true.
     *
     */
    public void generateWellFormednessClauses(List<VecInt> wellFormednessClauses, Integer varSlotId) {
        int[] leastOneIsTrue = new int[lattice.numTypes];
        for (Integer i : intToType.keySet()) {
            leastOneIsTrue[i] = MathUtils.mapIdToMatrixEntry(varSlotId, i.intValue(), lattice);
        }
        wellFormednessClauses.add(VectorUtils.asVec(leastOneIsTrue));
        List<Integer> varList = new ArrayList<Integer>(intToType.keySet());
        for (int i = 0; i < varList.size(); i++) {
            for (int j = i + 1; j < varList.size(); j++) {
                VecInt vecInt = new VecInt(2);
                vecInt.push(-MathUtils.mapIdToMatrixEntry(varSlotId, varList.get(i), lattice));
                vecInt.push(-MathUtils.mapIdToMatrixEntry(varSlotId, varList.get(j), lattice));
                wellFormednessClauses.add(vecInt);
            }
        }
    }

    @Override
    public AnnotationMirror decodeSolution(Integer var, ProcessingEnvironment processingEnvironment) {
        return intToType.get(MathUtils.getIntRep(var, lattice));
    }

}
