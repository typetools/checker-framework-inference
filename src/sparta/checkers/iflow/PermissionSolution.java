package sparta.checkers.iflow;

import sparta.checkers.iflow.util.PFPermission;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by smillst on 9/19/15.
 */
public class PermissionSolution {
    Map<Integer, Boolean> result;
    Map<Integer, Boolean> idToExistence;
    PFPermission permission;

    public PermissionSolution(Map<Integer, Boolean> result, Map<Integer, Boolean> idToExistence, PFPermission permission) {
        this.result = result;
        this.idToExistence = idToExistence;
        this.permission = permission;
    }

    /**
     * Used only to create a "no solution" PermissionSolution
     *
     * @param permission
     */
    private PermissionSolution(PFPermission permission) {
        this(new HashMap<Integer, Boolean>(), new HashMap<Integer, Boolean>(), permission);
    }

    public Map<Integer, Boolean> getIdToExistence() {
        return idToExistence;
    }

    public Map<Integer, Boolean> getResult() {
        return result;
    }

    public PFPermission getPermission() {
        return permission;
    }

    public static PermissionSolution noSolution(PFPermission permission) {
        return new PermissionSolution(permission);
    }
}
