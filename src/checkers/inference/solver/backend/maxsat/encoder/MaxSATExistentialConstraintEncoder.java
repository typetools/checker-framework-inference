package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.model.ExistentialConstraint;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * Created by mier on 07/11/17.
 */
public class MaxSATExistentialConstraintEncoder extends MaxSATAbstractConstraintEncoder implements ExistentialConstraintEncoder<VecInt[]> {

    public MaxSATExistentialConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, typeToInt);
    }

    @Override
    public VecInt[] encode(ExistentialConstraint constraint) {
        return defaultEncoding();
    }
}
