 package checkers.inference.solver.backend.z3;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.javacutil.ErrorReporter;

import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;
import com.microsoft.z3.Optimize;

import checkers.inference.InferenceMain;
import checkers.inference.model.Constraint;
import checkers.inference.model.PreferenceConstraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.SolverEnvironment;

public class Z3Solver extends Solver<Z3BitVectorFormatTranslator>{

    protected final Context context;
    protected final Optimize solver;
    protected final Z3BitVectorCodec z3BitVectorCodec;


    public Z3Solver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
            Collection<Constraint> constraints, Z3BitVectorFormatTranslator z3FormatTranslator, Lattice lattice) {
        super(solverEnvironment, slots, constraints, z3FormatTranslator, lattice);
        context = new Context();
        solver = context.mkOptimize();
        z3FormatTranslator.initContext(context);
        z3FormatTranslator.initSolver(solver);
        z3BitVectorCodec = z3FormatTranslator.getZ3BitVectorCodec();
    }

    @Override
    public Map<Integer, AnnotationMirror> solve() {
        Map<Integer, AnnotationMirror> result = new HashMap<>();

        encodeAllConstraints();

        switch (solver.Check()) {
            case SATISFIABLE: {
                result = decodeSolution(solver.getModel());
                break;
            }

            case UNSATISFIABLE: {
                System.out.println("Unsatisfiable!");
                break;
            }

            case UNKNOWN:
            default: {
                System.out.println("Solver failed to solve due to Unknown reason!");
                break;
            }
        }
        return result;
    }

    @Override
    public Collection<Constraint> explainUnsatisfiable() {
        return new HashSet<>();// Doesn't support right now
    }

    @Override
    protected void encodeAllConstraints() {
        for (Constraint constraint : constraints) {
            BoolExpr serializedConstraint = constraint.serialize(formatTranslator);

            if (serializedConstraint == null) {
                // TODO: Should error abort if unsupported constraint detected.
                // Currently warning is a workaround for making ontology working, as in some cases existential constraints generated.
                // Should investigate on this, and change this to ErrorAbort when eliminated unsupported constraints.
                InferenceMain.getInstance().logger.warning("Unsupported constraint detected! Constraint type: " + constraint.getClass());
                continue;
            } else if (serializedConstraint.isTrue()) {
                // Skip tautology.
                continue;
            }

            if (constraint instanceof PreferenceConstraint) {
                solver.AssertSoft(serializedConstraint, ((PreferenceConstraint) constraint).getWeight(), "preferCons");
            } else {
                solver.Assert(serializedConstraint);
            }
        }
    }


    protected Map<Integer, AnnotationMirror> decodeSolution(Model model) {
        Map<Integer, AnnotationMirror> result = new HashMap<>();

        for (FuncDecl funcDecl : model.getDecls()) {
            int slotId = Integer.valueOf(funcDecl.getName().toString());
            Expr constInterp = model.getConstInterp(funcDecl);
            if (! (constInterp instanceof BitVecNum)) {
                ErrorReporter.errorAbort("Wrong solution type detected: All solution must be type of BitVecNum, but get: " + constInterp.getClass());
            }

            result.put(slotId, formatTranslator.decodeSolution((BitVecNum) constInterp, solverEnvironment.processingEnvironment));
        }

        return result;
    }
 }
