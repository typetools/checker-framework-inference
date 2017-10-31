package checkers.inference.solver.util;

/**
 * String constants.
 * 
 * @author jianchu
 *
 */
public class Constants {

    public enum SolverArg {
        backEndType, useGraph, solveInParallel, collectStatistic;
    }

    public enum SlotType {
        ConstantSlot, VariableSlot;
    }

    public static final String TRUE = Boolean.TRUE.toString();
    public static final String FALSE = Boolean.FALSE.toString();
}
