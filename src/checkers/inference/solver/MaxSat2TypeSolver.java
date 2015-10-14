package checkers.inference.solver;

import checkers.inference.DefaultInferenceSolution;
import checkers.inference.InferenceMain;
import checkers.inference.InferenceSolver;
import checkers.inference.SlotManager;
import checkers.inference.InferenceSolution;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.serialization.CnfVecIntSerializer;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.sat4j.core.VecInt;
import org.sat4j.maxsat.SolverFactory;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.opt.MaxSatDecorator;
import org.sat4j.specs.ISolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This solver is used to convert any constraint set using a type system with only 2 types (Top/Bottom),
 * into a SAT problem.  This SAT problem is then solved by SAT4J and the output is converted back
 * into an InferenceSolution.
 */
public class MaxSat2TypeSolver implements InferenceSolver {

    // private QualifierHierarchy qualHierarchy;
    private Collection<Constraint> constraints;
    private Collection<Slot> slots;

    private AnnotationMirror defaultValue;
    private AnnotationMirror top;
    private AnnotationMirror bottom;
    private CnfVecIntSerializer serializer;
    private SlotManager slotManager;

    @Override
    public InferenceSolution solve(
            Map<String, String> configuration,
            Collection<Slot> slots,
            Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        this.slots = slots;
        this.constraints = constraints;
        // this.qualHierarchy = qualHierarchy;

        this.top = qualHierarchy.getTopAnnotations().iterator().next();
        this.bottom = qualHierarchy.getBottomAnnotations().iterator().next();
        this.slotManager = InferenceMain.getInstance().getSlotManager();
        this.serializer = new CnfVecIntSerializer(top, bottom, InferenceMain.getInstance().getSlotManager());
        // TODO: This needs to be parameterized based on the type system
        this.defaultValue = top;

        return solve();
    }

    public InferenceSolution solve() {

        Map<Integer, Boolean> idToExistence = new HashMap<>();
        Map<Integer, AnnotationMirror> result = new HashMap<>();

        List<VecInt> clauses = serializer.convertAll(constraints);

        //nextId describes the LARGEST id that might be found in a variable
        //if an exception occurs while creating a variable the id might be incremented
        //but the slot might not actually be recorded.  Therefore, nextId is NOT
        //the number of slots but the maximum you might encounter.
        final int totalVars = slotManager.nextId();
        final int totalClauses =  clauses.size();


        //When .newBoth is called, SAT4J will run two solvers and return the result of the first to halt
        final WeightedMaxSatDecorator solver = new WeightedMaxSatDecorator(org.sat4j.pb.SolverFactory.newBoth());

        solver.newVar(totalVars);
        solver.setExpectedNumberOfClauses(totalClauses);

        //arbitrary timeout selected for no particular reason
        solver.setTimeoutMs(1000000);

        VecInt lastClause = null;
        try {
            for (VecInt clause : clauses) {

                lastClause = clause;
                solver.addSoftClause(clause);
            }

            //isSatisfiable launches the solvers and waits until one of them finishes
            if (solver.isSatisfiable()) {
                final Map<Integer, Integer> existentialToPotentialIds = serializer.getExistentialToPotentialVar();
                int[] solution = solver.model();

                for (Integer var : solution) {
                    boolean isTop = var < 0;
                    if (isTop) {
                        var = -var;
                    }

                    Integer potential = existentialToPotentialIds.get(var);
                    if (potential != null) {
                        idToExistence.put(potential, !isTop);
                    } else {
                        result.put(var, isTop ? top : bottom );
                    }

                }
            } else {
                System.out.println("Not solvable!");
            }

        } catch(Throwable th) {
           throw new RuntimeException("Error MAX-SAT solving! " + lastClause, th);
        }


        return new DefaultInferenceSolution(result, idToExistence);
    }
}