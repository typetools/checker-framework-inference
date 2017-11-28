package sparta.checkers.sat;

import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import org.checkerframework.framework.type.QualifierHierarchy;
import sparta.checkers.iflow.util.PFPermission;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.*;

/**
 * Created by smillst on 9/17/15.
 */
public abstract class IFlowSolver implements InferenceSolver {


    public InferenceSolution solve(Map<String, String> configuration,
                                   Collection<Slot> slots,
                                   Collection<Constraint> constraints,
                                   QualifierHierarchy qualHierarchy,
                                   ProcessingEnvironment processingEnvironment) {
        Collection<PFPermission> permissionsUsed = getPermissionsUsed(slots);
        List<PermissionSolver> permissionSolvers = new ArrayList<>();

        // Configure permission solvers
        for (PFPermission permission : permissionsUsed) {
            PermissionSolver solver = new PermissionSolver(permission);
            solver.configure(constraints, getSerializer(permission));
            permissionSolvers.add(solver);
        }

        // Solve
        List<PermissionSolution> solutions = new ArrayList<>();
        for (PermissionSolver solver : permissionSolvers) {
            solutions.add(solver.solve());
        }

        return getMergedSolution(processingEnvironment, solutions);
    }

    private Collection<PFPermission> getPermissionsUsed(Collection<Slot> solts) {
        Set<PFPermission> permissions = new TreeSet<>();
        for (Slot slot : solts) {
            if (slot instanceof ConstantSlot) {
                ConstantSlot constantSlot = (ConstantSlot) slot;
                AnnotationMirror anno = constantSlot.getValue();
                permissions.addAll(getPermissionList(anno));
            }
        }
        // Ensure ANY is a permission used.
        permissions.add(PFPermission.ANY);
        return permissions;
    }

    protected abstract IFlowSerializer getSerializer(PFPermission permission);

    protected abstract InferenceSolution getMergedSolution(ProcessingEnvironment processingEnvironment, List<PermissionSolution> solutions);

    protected abstract Set<PFPermission> getPermissionList(AnnotationMirror anno);

}
