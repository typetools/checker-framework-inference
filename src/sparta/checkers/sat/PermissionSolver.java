package sparta.checkers.sat;

import checkers.inference.*;
import checkers.inference.model.Constraint;
import org.sat4j.core.VecInt;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import sparta.checkers.iflow.util.PFPermission;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionSolver {

    private SlotManager slotManager;
    private PFPermission permission;
    private IFlowSerializer serializer;
    List<VecInt> clauses;

    public PermissionSolver(PFPermission permission) {
        this.permission = permission;
    }

    public void configure(Collection<Constraint> constraints, IFlowSerializer serializer) {
        this.serializer = serializer;
        this.slotManager = InferenceMain.getInstance().getSlotManager();
        this.clauses = convertToCNF(constraints);
    }

    private List<VecInt> convertToCNF(Collection<Constraint> constraints) {
        return serializer.convertAll(constraints);
    }

    public PermissionSolution solve() {

        Map<Integer, Boolean> idToExistence = new HashMap<>();
        Map<Integer, Boolean> result = new HashMap<>();


        final int totalVars = slotManager.getNumberOfSlots();
        final int totalClauses = clauses.size();

        try {
            //**** Prep Solver ****
            //org.sat4j.pb.SolverFactory.newBoth() Runs both of sat4j solves and uses the result of the first to finish
            final WeightedMaxSatDecorator solver = new WeightedMaxSatDecorator(org.sat4j.pb.SolverFactory.newBoth());

            solver.newVar(totalVars);
            solver.setExpectedNumberOfClauses(totalClauses);
            //Arbitrary timeout
            solver.setTimeoutMs(1000000);
            for (VecInt clause : clauses) {
                solver.addSoftClause(clause);
            }

            //**** Solve ****
            boolean hasSolution = solver.isSatisfiable();

            if (hasSolution) {

                //**** Remove exatential vars from solution
                final Map<Integer, Integer> existentialToPotentialIds = serializer.getExistentialToPotentialVar();
                int[] solution = solver.model();

                for (Integer var : solution) {
                    boolean varIsTrue = !(var < 0);
                    //Need postive var
                    var = Math.abs(var);

                    Integer potential = existentialToPotentialIds.get(var);
                    if (potential != null) {
                        idToExistence.put(potential, varIsTrue);
                    } else {
                        result.put(var, varIsTrue);
                    }
                }
                return new PermissionSolution(result, idToExistence, permission);
            }

        } catch (Throwable th) {
            VecInt lastClause = clauses.get(clauses.size() - 1);
            throw new RuntimeException("Error MAX-SAT solving! " + lastClause, th);
        }

        return PermissionSolution.noSolution(permission);
    }
}