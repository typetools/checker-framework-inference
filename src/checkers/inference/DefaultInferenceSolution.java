package checkers.inference;

import java.util.Map;

import javax.lang.model.element.AnnotationMirror;

public class DefaultInferenceSolution implements InferenceSolution {

    private final Map<Integer, AnnotationMirror> varIdToAnnotation;

    public DefaultInferenceSolution(Map<Integer, AnnotationMirror> varIdToAnnotation) {
        this.varIdToAnnotation = varIdToAnnotation;
    }

    @Override
    public boolean doesVariableExist(int variableId) {
        return varIdToAnnotation.containsKey(variableId);
    }

    @Override
    public AnnotationMirror getAnnotation(int variableId) {
        return varIdToAnnotation.get(variableId);
    }
}

