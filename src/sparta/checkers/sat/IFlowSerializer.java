package sparta.checkers.sat;

import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import sparta.checkers.CnfVecIntSerializer;
import sparta.checkers.iflow.util.PFPermission;

/**
 * Created by smillst on 9/16/15.
 */
public abstract class IFlowSerializer extends CnfVecIntSerializer {
    private SlotManager slotManager;
    protected PFPermission permission;

    public IFlowSerializer(PFPermission permission) {
        super(InferenceMain.getInstance().getSlotManager());
        this.slotManager = InferenceMain.getInstance().getSlotManager();
        this.permission = permission;
    }
}
