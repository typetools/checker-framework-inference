package sparta.checkers.iflow;

import checkers.inference.InferenceSolution;
import org.checkerframework.javacutil.AnnotationUtils;
import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;
import sparta.checkers.quals.PolySink;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by smillst on 9/21/15.
 */
public class SinkSolver extends IFlowSolver {
    @Override
    protected Set<PFPermission> getPermissionList(AnnotationMirror anno) {
        if (AnnotationUtils.areSameByClass(anno, PolySink.class)) {
            return new HashSet<>();
        }
        return IFlowUtils.getSinks(anno);
    }

    @Override
    protected IFlowSerializer getSerializer(PFPermission permission) {
        return new SinkSerializer(permission);
    }

    @Override
    protected InferenceSolution getMergedSolution(ProcessingEnvironment processingEnvironment, List<PermissionSolution> solutions) {
        return new SinkSolution(solutions, processingEnvironment);
    }
}
