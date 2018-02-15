package sparta.checkers.sat;

import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.*;

/**
 * Created by smillst on 9/21/15.
 */
public class SourceResult extends IFlowResult {

    public SourceResult(Collection<PermissionSolution> solutions, ProcessingEnvironment processingEnv) {
        super(solutions, processingEnv);
    }

    protected boolean shouldContainPermission(Map.Entry<Integer, Boolean> entry) {
        // If the solution is false, that means top was infered
        // for sources, that means that the annotation should have the permission
        return !entry.getValue();
    }

    @Override
    protected AnnotationMirror createAnnotationFromPermissions(ProcessingEnvironment processingEnv, Set<PFPermission> permissions) {
        return IFlowUtils.createAnnoFromSource(permissions, processingEnv);
    }
}
