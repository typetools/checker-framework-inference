package sparta.checkers.sat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.DefaultInferenceResult;
import checkers.inference.InferenceMain;
import sparta.checkers.iflow.util.PFPermission;

/**
 * Created by smillst on 9/21/15.
 */
public abstract class IFlowResult extends DefaultInferenceResult {
    protected final Map<Integer, Set<PFPermission>> tempResults;
    protected final Map<Integer, Boolean> idToExistance;

    public IFlowResult(Collection<PermissionSolution> solutions, ProcessingEnvironment processingEnv) {
        // Legacy solver doesn't support explanation
        super();
        this.tempResults = new HashMap<>();
        this.idToExistance = new HashMap<>();

        mergeSolutions(solutions);
        createAnnotations(processingEnv);
    }

    private void mergeSolutions(Collection<PermissionSolution> solutions) {
        for (PermissionSolution solution : solutions) {
            mergeSingleSolution(solution);
            mergeIdToExistance(solution);
        }
    }

    private void mergeSingleSolution(PermissionSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            boolean shouldContainPermission = shouldContainPermission(entry);
            PFPermission permission = solution.getPermission();

            Set<PFPermission> permissions = tempResults.get(entry.getKey());
            if (permissions == null) {
                permissions = new TreeSet<>();
                tempResults.put(entry.getKey(), permissions);
            }

            if (shouldContainPermission) {
                permissions.add(permission);
            }

            if (permissions.contains(PFPermission.ANY) && permissions.size() > 1) {
                permissions.clear();
                permissions.add(PFPermission.ANY);
            }
        }
    }

    protected abstract boolean shouldContainPermission(Map.Entry<Integer, Boolean> entry);

    private void createAnnotations(ProcessingEnvironment processingEnv) {
        for (Map.Entry<Integer, Set<PFPermission>> entry : tempResults.entrySet()) {
            int id = entry.getKey();
            Set<PFPermission> permissions = entry.getValue();
            AnnotationMirror anno = createAnnotationFromPermissions(processingEnv, permissions);
            varIdToAnnotation.put(id, anno);
        }
    }

    protected abstract AnnotationMirror createAnnotationFromPermissions(ProcessingEnvironment processingEnv, Set<PFPermission> permissions);

    private void mergeIdToExistance(PermissionSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            int id = entry.getKey();
            boolean existsPermission = entry.getValue();
            if (idToExistance.containsKey(id)) {
                boolean alreadyExists = idToExistance.get(id);
                if (alreadyExists ^ existsPermission) {
                    InferenceMain.getInstance().logger.log(Level.INFO, "Mismatch between existance of annotation");
                }
            } else {
                idToExistance.put(id, existsPermission);
            }
        }
    }

    // TODO
    // Mier: I'm worried that this causes inconsistency with getSolutionForVariable(), as it uses
    // a different map - varIdToAnnotation to store the actual var id to annotation solution.
    @Override
    public boolean containsSolutionForVariable(int varId) {
        return idToExistance.containsKey(varId);
    }

}
