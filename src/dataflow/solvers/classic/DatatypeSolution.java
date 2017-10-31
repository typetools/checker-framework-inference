package dataflow.solvers.classic;

import java.util.HashMap;
import java.util.Map;

public class DatatypeSolution {
    private final Map<Integer, Boolean> result;
    private final String datatype;
    private final boolean isRoot;

    public DatatypeSolution(Map<Integer, Boolean> result, String datatype, boolean isRoot) {
        this.result = result;
        this.datatype = datatype;
        this.isRoot = isRoot;
    }

    private DatatypeSolution(String datatype, boolean isRoot) {
        this(new HashMap<Integer, Boolean>(), datatype, isRoot);
    }

    public Map<Integer, Boolean> getResult() {
        return result;
    }

    public String getDatatype() {
        return datatype;
    }

    public static DatatypeSolution noSolution(String datatype) {
        return new DatatypeSolution(datatype, false);
    }

    public boolean isRoot() {
        return this.isRoot;
    }

}
