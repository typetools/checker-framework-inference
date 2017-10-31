package checkers.inference.solver.backend.maxsat;

import checkers.inference.solver.frontend.Lattice;

/**
 * Methods convert between slot id and Max-SAT id.
 * 
 * @author jianchu
 *
 */
public class MathUtils {

    /**
     * Given a varSlot id, the integer representation of a type, and the lattice, map these to
     * an int represents the boolean variable in SAT solver, that represents the case of
     * this varSlot is the given type. (so the negative of the return value would be
     * this varSlot is not the given type.)
     * @param varId the slot id of the varSlot
     * @param typeInt the integer representation of a type
     * @param lattice  target lattice
     * @return an integer represents a boolean variable in SAT solver
     */
    public static int mapIdToMatrixEntry(int varId, int typeInt, Lattice lattice) {
        int column = typeInt + 1;
        int row = varId - 1;
        int length = lattice.numTypes;
        return column + row * length;
    }

    public static int getSlotId(int var, Lattice lattice) {
        return (Math.abs(var) / lattice.numTypes + 1);
    }

    public static int getIntRep(int var, Lattice lattice) {
        return Math.abs(var) - (Math.abs(var) / lattice.numTypes) * lattice.numTypes;
    }
}
