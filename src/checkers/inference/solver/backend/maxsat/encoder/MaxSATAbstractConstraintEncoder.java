package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoder;
import checkers.inference.solver.backend.maxsat.VectorUtils;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.util.ConstraintVerifier;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * Created by mier on 06/11/17.
 */
public abstract class MaxSATAbstractConstraintEncoder extends AbstractConstraintEncoder<VecInt[]> {

    private static VecInt[] EMPTY_CLAUSE = new VecInt[0];
    private static VecInt[] CONTRADICTORY_CLAUSES = new VecInt[]{VectorUtils.asVec(1), VectorUtils.asVec(-1)};

    protected final Map<AnnotationMirror, Integer> typeToInt;

    public MaxSATAbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, EMPTY_CLAUSE, CONTRADICTORY_CLAUSES);
        this.typeToInt = typeToInt;
    }
}
