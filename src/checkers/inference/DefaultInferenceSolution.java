package checkers.inference;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

//TODO: FIX THIS TO USE WERNER'S IDEA OF BEING ABLE TO ITERATE
//TODO: OVER ENTRIES THAT CONTAIN BOTH THE INFERRED ANNOTATIONMIRROR
//TODO: AND WHETHER OR NOT IT EXISTS.  THIS WAY WE DON'T HAVE TO
//TODO: QUERY TWO LARGE MAPS
public class DefaultInferenceSolution implements InferenceSolution {

    private final Map<Integer, AnnotationMirror> varIdToAnnotation;
    private final Map<Integer, Boolean> idToExistance;

    public DefaultInferenceSolution(Map<Integer, AnnotationMirror> varIdToAnnotation, Map<Integer, Boolean> idToExistance) {
        this.varIdToAnnotation = varIdToAnnotation;
        this.idToExistance = idToExistance;
    }

    public Map<Integer, AnnotationMirror> getVarIdToAnnotation() {
        return varIdToAnnotation;
    }

    public Map<Integer, Boolean> getIdToExistance() {
        return idToExistance;
    }

    public boolean doesVariableExist(int variableId) {
        Boolean exists = idToExistance.get(variableId);
        return exists == null;
    }

    public AnnotationMirror getAnnotation(int variableId) {
        return varIdToAnnotation.get(variableId);
    }
}

