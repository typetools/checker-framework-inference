package sparta.checkers.sat;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceSolution;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import sparta.checkers.iflow.util.PFPermission;

/** Created by smillst on 9/21/15. */
public abstract class IFlowSolution implements InferenceSolution {
    protected final Map<Integer, Set<PFPermission>> results;
    protected final Map<Integer, Boolean> idToExistance;
    protected final Map<Integer, AnnotationMirror> annotationResults;

    public IFlowSolution(
            Collection<PermissionSolution> solutions, ProcessingEnvironment processingEnv) {
        this.results = new HashMap<>();
        this.idToExistance = new HashMap<>();
        this.annotationResults = new HashMap<>();

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
        for (Map.Entry<Integer, Set<PFPermission>> entry : results.entrySet()) {
            int id = entry.getKey();
            Set<PFPermission> permissions = entry.getValue();
            AnnotationMirror anno = createAnnotationFromPermissions(processingEnv, permissions);
            annotationResults.put(id, anno);
        }
    }

    protected abstract AnnotationMirror createAnnotationFromPermissions(
            ProcessingEnvironment processingEnv, Set<PFPermission> permissions);

    private void mergeIdToExistance(PermissionSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            int id = entry.getKey();
            boolean existsPermission = entry.getValue();
            if (idToExistance.containsKey(id)) {
                boolean alreadyExists = idToExistance.get(id);
                if (alreadyExists ^ existsPermission) {
                    InferenceMain.getInstance()
                            .logger
                            .log(Level.INFO, "Mismatch between existance of annotation");
                }
            } else {
                idToExistance.put(id, existsPermission);
            }
        }
    }

    @Override
    public boolean doesVariableExist(int varId) {
        return idToExistance.containsKey(varId);
    }

    @Override
    public AnnotationMirror getAnnotation(int varId) {
        return annotationResults.get(varId);
    }
}
