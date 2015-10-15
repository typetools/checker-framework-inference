package sparta.checkers.sat;

import checkers.inference.InferenceSolution;
import org.checkerframework.javacutil.AnnotationUtils;
import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;
import sparta.checkers.quals.PolySource;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.*;

/**
 * Created by smillst on 9/17/15.
 */
public class SourceSolver extends IFlowSolver {

    protected Set<PFPermission> getPermissionList(AnnotationMirror anno) {
        if (AnnotationUtils.areSameByClass(anno, PolySource.class)) {
            return new HashSet<>();
        }
        return IFlowUtils.getSources(anno);
    }

    @Override
    protected IFlowSerializer getSerializer(PFPermission permission) {
        return new SourceSerializer(permission);
    }

    protected InferenceSolution getMergedSolution(ProcessingEnvironment processingEnvironment, List<PermissionSolution> solutions) {
        return new SourceSolution(solutions, processingEnvironment);
    }
}
