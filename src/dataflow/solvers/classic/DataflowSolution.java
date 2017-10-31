package dataflow.solvers.classic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceSolution;
import checkers.inference.solver.util.PrintUtils;
import dataflow.DataflowAnnotatedTypeFactory;
import dataflow.util.DataflowUtils;

public class DataflowSolution implements InferenceSolution {
    private final Map<Integer, Set<String>> typeNameResults;
    private final Map<Integer, Set<String>> typeRootResults;
    private final Map<Integer, Boolean> idToExistance;
    private final Map<Integer, AnnotationMirror> annotationResults;
    private final DataflowAnnotatedTypeFactory realTypeFactory;

    public DataflowSolution(Collection<DatatypeSolution> solutions, ProcessingEnvironment processingEnv) {
        this.typeNameResults = new HashMap<>();
        this.typeRootResults = new HashMap<>();
        this.idToExistance = new HashMap<>();
        this.annotationResults = new HashMap<>();
        this.realTypeFactory = (DataflowAnnotatedTypeFactory)InferenceMain.getInstance().getRealTypeFactory();
        merge(solutions);
        createAnnotations(processingEnv);
        simplifyAnnotation();
        PrintUtils.printResult(annotationResults);
    }

    public void merge(Collection<DatatypeSolution> solutions) {
        for (DatatypeSolution solution : solutions) {
            mergeResults(solution);
            mergeIdToExistance(solution);
        }
    }

    private void mergeResults(DatatypeSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            boolean shouldContainDatatype = shouldContainDatatype(entry);
            String datatype = solution.getDatatype();
            if (solution.isRoot()) {
                Set<String> dataRoots = typeRootResults.get(entry.getKey());
                if (dataRoots == null) {
                    dataRoots = new TreeSet<>();
                    typeRootResults.put(entry.getKey(), dataRoots);
                }
                if (shouldContainDatatype) {
                    dataRoots.add(datatype);
                }
            } else {
                Set<String> datatypes = typeNameResults.get(entry.getKey());
                if (datatypes == null) {
                    datatypes = new TreeSet<>();
                    typeNameResults.put(entry.getKey(), datatypes);
                }
                if (shouldContainDatatype) {
                    datatypes.add(datatype);
                }
            }
        }
    }

    protected boolean shouldContainDatatype(Map.Entry<Integer, Boolean> entry) {
        return entry.getValue();
    }

    private void createAnnotations(ProcessingEnvironment processingEnv) {
        for (Map.Entry<Integer, Set<String>> entry : typeNameResults.entrySet()) {
            int slotId = entry.getKey();
            Set<String> datatypes = entry.getValue();
            Set<String> roots = typeRootResults.get(slotId);
            AnnotationMirror anno;
            if (roots != null) {
                anno = DataflowUtils.createDataflowAnnotationWithRoots(datatypes, typeRootResults.get(slotId), processingEnv);
            } else {
                anno = DataflowUtils.createDataflowAnnotation(datatypes, processingEnv);
            }
            annotationResults.put(slotId, anno);
        }
        
        for (Map.Entry<Integer, Set<String>> entry : typeRootResults.entrySet()) {
            int slotId = entry.getKey();
            Set<String> roots = entry.getValue();
            Set<String> typeNames = typeNameResults.get(slotId);
            if (typeNames == null) {
                AnnotationMirror anno = DataflowUtils.createDataflowAnnotationWithoutName(roots, processingEnv);
                annotationResults.put(slotId, anno);
            }
        }

    }

    private void simplifyAnnotation() {
        for (Map.Entry<Integer, AnnotationMirror> entry : annotationResults.entrySet()) {
            AnnotationMirror refinedDataflow = this.realTypeFactory.refineDataflow(entry.getValue());
            entry.setValue(refinedDataflow);
        }
    }

    private void mergeIdToExistance(DatatypeSolution solution) {
        for (Map.Entry<Integer, Boolean> entry : solution.getResult().entrySet()) {
            int id = entry.getKey();
            boolean existsDatatype = entry.getValue();
            if (idToExistance.containsKey(id)) {
                boolean alreadyExists = idToExistance.get(id);
                if (alreadyExists ^ existsDatatype) {
                    InferenceMain.getInstance().logger.log(Level.INFO, "Mismatch between existance of annotation");
                }
            } else {
                idToExistance.put(id, existsDatatype);
            }
        }
    }

    @Override
    public boolean doesVariableExist(int varId) {
        return idToExistance.containsKey(varId);
    }

    @Override
    public AnnotationMirror getAnnotation(int varId) {
        return annotationResults.get(varId);
    }

}
