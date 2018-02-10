package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.VariableSlot;
import checkers.inference.solver.backend.encoder.preference.PreferenceConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MathUtils;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

public class MaxSATPreferenceConstraintEncoder extends MaxSATAbstractConstraintEncoder implements PreferenceConstraintEncoder<VecInt[]> {

    public MaxSATPreferenceConstraintEncoder(Lattice lattice, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, typeToInt);
    }

    // TODO: we should consider the situation that the type annotations with
    // different weights.
    @Override
    public VecInt[] encode(PreferenceConstraint constraint) {
        VariableSlot vs = constraint.getVariable();
        ConstantSlot cs = constraint.getGoal();
        if (lattice.allTypes.contains(cs.getValue())) {
            return VectorUtils.asVecArray(MathUtils.mapIdToMatrixEntry(vs.getId(), typeToInt.get(cs.getValue()),
                    lattice));
        } else {
            return emptyValue;
        }
    }
}
