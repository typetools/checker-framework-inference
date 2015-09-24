package sparta.checkers.iflow;

import checkers.inference.model.ConstantSlot;
import sparta.checkers.iflow.util.IFlowUtils;
import sparta.checkers.iflow.util.PFPermission;

import javax.lang.model.element.AnnotationMirror;
import java.util.Set;

/**
 * Created by smillst on 9/21/15.
 */
public class SourceSerializer extends IFlowSerializer {
    public SourceSerializer(PFPermission permission) {
        super(permission);
    }

    @Override
    public boolean isTop(ConstantSlot constantSlot) {
        AnnotationMirror anno = constantSlot.getValue();
        return annoHasPermission(anno);
    }

    private boolean annoHasPermission(AnnotationMirror anno) {
        Set<PFPermission> sources = IFlowUtils.getSources(anno);
        return sources.contains(PFPermission.ANY) || sources.contains(permission);
    }
}
