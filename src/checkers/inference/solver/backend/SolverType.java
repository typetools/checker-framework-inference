package checkers.inference.solver.backend;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.inference.util.ConstraintVerifier;
import org.checkerframework.javacutil.ErrorReporter;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.logiql.LogiQLFormatTranslator;
import checkers.inference.solver.backend.logiql.LogiQLSolver;
import checkers.inference.solver.backend.maxsat.LingelingSolver;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.backend.maxsat.MaxSatSolver;
import checkers.inference.solver.backend.z3.Z3BitVectorFormatTranslator;
import checkers.inference.solver.backend.z3.Z3Solver;
import checkers.inference.solver.frontend.Lattice;

public enum SolverType {

    MAXSAT("MaxSAT", MaxSatSolver.class, MaxSatFormatTranslator.class),
    LINGELING("Lingeling", LingelingSolver.class, MaxSatFormatTranslator.class),
    LOGIQL("LogiQL", LogiQLSolver.class, LogiQLFormatTranslator.class),
    // Currently we don't have default Z3 format translator.
    Z3("Z3", Z3Solver.class, Z3BitVectorFormatTranslator.class);

    public final String simpleName;
    public final Class<? extends SolverAdapter<?>> solverAdapterClass;
    public final Class<? extends FormatTranslator<?, ?, ?>> translatorClass;

    private SolverType(String simpleName, Class<? extends SolverAdapter<?>> solverAdapterClass,
            Class<? extends FormatTranslator<?, ?, ?>> translatorClass) {
        this.simpleName = simpleName;
        this.solverAdapterClass = solverAdapterClass;
        this.translatorClass = translatorClass;
    }

    public FormatTranslator<?, ?, ?> createDefaultFormatTranslator(Lattice lattice, ConstraintVerifier verifier) {
        Constructor<?> cons;
        try {
            cons = translatorClass.getConstructor(Lattice.class, ConstraintVerifier.class);
            return (FormatTranslator<?, ?, ?>) cons.newInstance(lattice, verifier);
        } catch (Exception e) {
            ErrorReporter.errorAbort(
                    "Exception happens when creating default format translator for " + simpleName + " backend.", e);
            // Dead code.
            return null;
        }
    }

    public SolverAdapter<?> createSolverAdapter(Map<String, String> configuration,
            Collection<Slot> slots, Collection<Constraint> constraints,
            ProcessingEnvironment processingEnvironment, Lattice lattice,
            FormatTranslator<?, ?, ?> formatTranslator) {
        try {
            Constructor<?> cons = solverAdapterClass.getConstructor(Map.class, Collection.class,
                    Collection.class, ProcessingEnvironment.class, translatorClass, Lattice.class);

            return (SolverAdapter<?>) cons.newInstance(configuration, slots, constraints,
                    processingEnvironment, formatTranslator, lattice);
        } catch (Exception e) {
            ErrorReporter.errorAbort(
                    "Exception happends when creating " + simpleName + " backend.", e);
            // Dead code.
            return null;
        }
    }

    public static SolverType getSolverType(String simpleName) {
        for (SolverType solverType : SolverType.values()) {
            if (solverType.simpleName.equals(simpleName)) {
                return solverType;
            }
        }
        return null;
    }
}
