package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * MaxSAT implementation of {@link checkers.inference.solver.backend.encoder.ConstraintEncoderFactory}.
 *
 * @see checkers.inference.solver.backend.encoder.ConstraintEncoderFactory
 */
public class MaxSATConstraintEncoderFactory extends AbstractConstraintEncoderFactory<VecInt[]> {

    private final Map<AnnotationMirror, Integer> typeToInt;

    public MaxSATConstraintEncoderFactory(Lattice lattice, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice);
        this.typeToInt = typeToInt;
    }

    @Override
    public MaxSATSubtypeConstraintEncoder createSubtypeConstraintEncoder() {
        return new MaxSATSubtypeConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATEqualityConstraintEncoder createEqualityConstraintEncoder() {
        return new MaxSATEqualityConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATInequalityConstraintEncoder createInequalityConstraintEncoder() {
        return new MaxSATInequalityConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATComparableConstraintEncoder createComparableConstraintEncoder() {
        return new MaxSATComparableConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATPreferenceConstraintEncoder createPreferenceConstraintEncoder() {
        return new MaxSATPreferenceConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public CombineConstraintEncoder<VecInt[]> createCombineConstraintEncoder() {
        return null;
    }

    @Override
    public ExistentialConstraintEncoder<VecInt[]> createExistentialConstraintEncoder() {
        return null;
    }
}
