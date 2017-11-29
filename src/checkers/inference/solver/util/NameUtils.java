package checkers.inference.solver.util;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.strategy.SolvingStrategy;

public class NameUtils {

    public static String getSimpleName(AnnotationMirror annoMirror) {
        final DeclaredType annoType = annoMirror.getAnnotationType();
        final TypeElement elm = (TypeElement) annoType.asElement();
        return elm.getSimpleName().toString().intern();
    }

    /**
     * Given a strategy class, return the strategy name by removing the common naming suffix.
     * E.g. Given PlainSolvingStrategy, this method return "Plain".
     *
     */
    public static String getStrategyName(Class<? extends SolvingStrategy> strategyClass) {
        final String strategyClassName = strategyClass.getSimpleName();
        final String strategySuffix = SolvingStrategy.class.getSimpleName();
        return strategyClassName.substring(0, strategyClassName.length() - strategySuffix.length());
    }

    /**
     * Given a solver class, return the solver name by removing the common naming suffix.
     * E.g. Given MaxSatSolver, this method return "MaxSat".
     *
     */
    public static String getSolverName(Class<? extends Solver<?>> solverClass) {
        final String solverClassName = solverClass.getSimpleName();
        final String solverSuffix = Solver.class.getSimpleName();
        return solverClassName.substring(0, solverClassName.length() - solverSuffix.length());
    }
}
