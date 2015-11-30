package sparta.checkers.sat;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceSolution;
import sparta.checkers.iflow.util.PFPermission;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.*;
import java.util.logging.Level;

/**
 * Created by smillst on 9/21/15.
 */
public abstract class IFlowSolution implements InferenceSolution {
    Map<Integer, Set<PFPermission>> results;
    Map<Integer, Boolean> idToExistance;
    Map<Integer, AnnotationMirror> annotationResults;

    public IFlowSolution(Collection<PermissionSolution> solutions, ProcessingEnvironment processingEnv) {
        this.results = new HashMap<>();
        this.idToExistance = new HashMap<>();
        merge(solutions);
        createAnnotations(processingEnv);
    }

    private void merge(Collection<PermissionSolution> solutions) {
        for (PermissionSolution solution : solutions) {
            mergeResults(solution);
            mergeIdToExistance(solution);
        }
    }

    private void mergeResults(PermissionSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            boolean shouldContainPermission = shouldContainPermission(entry);
            PFPermission permission = solution.getPermission();

            Set<PFPermission> permissions = results.get(entry.getKey());
            if (permissions == null) {
                permissions = new TreeSet<>();
                results.put(entry.getKey(), permissions);
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
        annotationResults = new HashMap<>();
        for (Map.Entry<Integer, Set<PFPermission>> entry : results.entrySet()) {
            int id = entry.getKey();
            Set<PFPermission> permissions = entry.getValue();
            AnnotationMirror anno = createAnnotationFromPermissions(processingEnv, permissions);
            annotationResults.put(id, anno);
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

    @Override
    public Map<Integer, AnnotationMirror> getVarIdToAnnotation() {
        return annotationResults;
    }

    @Override
    public boolean doesVariableExist(int varId) {
        return idToExistance.containsKey(varId);
    }

    @Override
    public AnnotationMirror getAnnotation(int varId) {
        return annotationResults.get(varId);
    }

    @Override
    public Map<Integer, Boolean> getIdToExistance() {
        return idToExistance;
    }
}
