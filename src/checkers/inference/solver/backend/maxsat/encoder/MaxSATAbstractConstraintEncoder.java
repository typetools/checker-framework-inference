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

    private static VecInt[] emptyClause = new VecInt[0];
    // Here it uses 1,-1 in which 1 is almost always a variable id reserved
    // for real qualifier. I didn't find a clean way to pass number of expected variables
    // then plus 1 and use that here except by adding a new field numberOfVariables in Lattice
    // class. But I'm not sure if Lattice should care about numberOfVariables or not.
    private static VecInt[] contradictoryClauses = new VecInt[]{VectorUtils.asVec(1), VectorUtils.asVec(-1)};

    protected final Map<AnnotationMirror, Integer> typeToInt;

    public MaxSATAbstractConstraintEncoder(Lattice lattice, ConstraintVerifier verifier, Map<AnnotationMirror, Integer> typeToInt) {
        super(lattice, verifier, emptyClause, contradictoryClauses);
        this.typeToInt = typeToInt;
    }
}
