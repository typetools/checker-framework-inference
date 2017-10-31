package checkers.inference.solver.backend.maxsat;

import org.sat4j.core.VecInt;

/**
 * Helper class for creating VecInt and VecInt array.
 * 
 * @author jianchu
 *
 */
public class VectorUtils {

    public static VecInt asVec(int... result) {
        return new VecInt(result);
    }

    public static VecInt[] asVecArray(int... vars) {
        return new VecInt[] { new VecInt(vars) };
    }

}
