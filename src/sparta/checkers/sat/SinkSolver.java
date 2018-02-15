package sparta.checkers.sat;

import checkers.inference.InferenceResult;
import org.checkerframework.javacutil.AnnotationUtils;
import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;
import sparta.checkers.qual.PolySink;

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
    protected InferenceResult getMergedResultFromSolutions(ProcessingEnvironment processingEnvironment, List<PermissionSolution> solutions) {
        return new SinkResult(solutions, processingEnvironment);
    }
}
