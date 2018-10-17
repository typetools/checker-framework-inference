package dataflow.solvers.general;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

import checkers.inference.DefaultInferenceResult;
import com.sun.tools.javac.util.Pair;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;

import checkers.inference.InferenceMain;
import checkers.inference.InferenceResult;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.Solver;
import checkers.inference.solver.backend.SolverFactory;
import checkers.inference.solver.constraintgraph.ConstraintGraph;
import checkers.inference.solver.constraintgraph.GraphBuilder;
import checkers.inference.solver.constraintgraph.Vertex;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.frontend.LatticeBuilder;
import checkers.inference.solver.frontend.TwoQualifiersLattice;
import checkers.inference.solver.strategy.GraphSolvingStrategy;
import checkers.inference.solver.util.PrintUtils;
import checkers.inference.solver.util.SolverEnvironment;
import checkers.inference.solver.util.Statistics;
import dataflow.DataflowAnnotatedTypeFactory;
import dataflow.qual.DataFlow;
import dataflow.qual.DataFlowInferenceBottom;
import dataflow.qual.DataFlowTop;
import dataflow.util.DataflowUtils;

public class DataflowGraphSolvingStrategy extends GraphSolvingStrategy {

    protected ProcessingEnvironment processingEnvironment;

    public DataflowGraphSolvingStrategy(SolverFactory solverFactory) {
        super(solverFactory);
    }

    @Override
    public InferenceResult solve(SolverEnvironment solverEnvironment, Collection<Slot> slots,
                                 Collection<Constraint> constraints, Lattice lattice) {
        this.processingEnvironment = solverEnvironment.processingEnvironment;
        return super.solve(solverEnvironment, slots, constraints, lattice);
    }

    @Override
    protected List<Solver<?>> separateGraph(SolverEnvironment solverEnvironment, ConstraintGraph constraintGraph,
            Collection<Slot> slots, Collection<Constraint> constraints, Lattice lattice) {
        AnnotationMirror DATAFLOW = AnnotationBuilder.fromClass(processingEnvironment.getElementUtils(), DataFlow.class);
        AnnotationMirror DATAFLOWBOTTOM = AnnotationBuilder.fromClass(processingEnvironment.getElementUtils(),
                DataFlowInferenceBottom.class);

        List<Solver<?>> solvers = new ArrayList<>();
        Statistics.addOrIncrementEntry("graph_size", constraintGraph.getConstantPath().size());

        for (Map.Entry<Vertex, Set<Constraint>> entry : constraintGraph.getConstantPath().entrySet()) {
            AnnotationMirror anno = entry.getKey().getValue();
            if (AnnotationUtils.areSameIgnoringValues(anno, DATAFLOW)) {
                String[] dataflowValues = DataflowUtils.getTypeNames(anno);
                String[] dataflowRoots = DataflowUtils.getTypeNameRoots(anno);
                if (dataflowValues.length == 1) {
                    AnnotationMirror DATAFLOWTOP = DataflowUtils.createDataflowAnnotation(
                            DataflowUtils.convert(dataflowValues), processingEnvironment);
                    TwoQualifiersLattice latticeFor2 = new LatticeBuilder().buildTwoTypeLattice(DATAFLOWTOP, DATAFLOWBOTTOM);
                    solvers.add(solverFactory.createSolver(solverEnvironment, slots, entry.getValue(), latticeFor2));
                } else if (dataflowRoots.length == 1) {
                    AnnotationMirror DATAFLOWTOP = DataflowUtils.createDataflowAnnotationForByte(
                            DataflowUtils.convert(dataflowRoots), processingEnvironment);
                    TwoQualifiersLattice latticeFor2 = new LatticeBuilder().buildTwoTypeLattice(DATAFLOWTOP, DATAFLOWBOTTOM);
                    solvers.add(solverFactory.createSolver(solverEnvironment, slots, entry.getValue(), latticeFor2));
                }
            }
        }

        return solvers;
    }

    @Override
    protected ConstraintGraph generateGraph(Collection<Slot> slots, Collection<Constraint> constraints,
            ProcessingEnvironment processingEnvironment) {
        AnnotationMirror DATAFLOWTOP = AnnotationBuilder.fromClass(
                processingEnvironment.getElementUtils(), DataFlowTop.class);
        GraphBuilder graphBuilder = new GraphBuilder(slots, constraints, DATAFLOWTOP);
        ConstraintGraph constraintGraph = graphBuilder.buildGraph();
        return constraintGraph;
    }

    @Override
    protected InferenceResult mergeInferenceResults(List<Pair<Map<Integer, AnnotationMirror>, Collection<Constraint>>> inferenceResults) {
        Map<Integer, AnnotationMirror> solutions = new HashMap<>();
        Map<Integer, Set<AnnotationMirror>> dataflowResults = new HashMap<>();

        for (Pair<Map<Integer, AnnotationMirror>, Collection<Constraint>> inferenceResult : inferenceResults) {
            Map<Integer, AnnotationMirror> inferenceSolutionMap = inferenceResult.fst;
            if (inferenceResult.fst != null) {
                for (Map.Entry<Integer, AnnotationMirror> entry : inferenceSolutionMap.entrySet()) {
                    Integer id = entry.getKey();
                    AnnotationMirror dataflowAnno = entry.getValue();
                    if (AnnotationUtils.areSameByClass(dataflowAnno, DataFlow.class)) {
                        Set<AnnotationMirror> datas = dataflowResults.get(id);
                        if (datas == null) {
                            datas = AnnotationUtils.createAnnotationSet();
                            dataflowResults.put(id, datas);
                        }
                        datas.add(dataflowAnno);
                    }
                }
            } else {
                // If any sub solution is null, there is no solution in a whole.
                return new DefaultInferenceResult(inferenceResult.snd);
            }
        }

        for (Map.Entry<Integer, Set<AnnotationMirror>> entry : dataflowResults.entrySet()) {
            Set<String> dataTypes = new HashSet<String>();
            Set<String> dataRoots = new HashSet<String>();
            for (AnnotationMirror anno : entry.getValue()) {
                String[] dataTypesArr = DataflowUtils.getTypeNames(anno);
                String[] dataRootsArr = DataflowUtils.getTypeNameRoots(anno);
                if (dataTypesArr.length == 1) {
                    dataTypes.add(dataTypesArr[0]);
                }
                if (dataRootsArr.length == 1) {
                    dataRoots.add(dataRootsArr[0]);
                }
            }
            AnnotationMirror dataflowAnno = DataflowUtils.createDataflowAnnotationWithRoots(dataTypes,
                    dataRoots, processingEnvironment);
            solutions.put(entry.getKey(), dataflowAnno);
        }
        for (Map.Entry<Integer, AnnotationMirror> entry : solutions.entrySet()) {
            AnnotationMirror refinedDataflow = ((DataflowAnnotatedTypeFactory) InferenceMain
                    .getInstance().getRealTypeFactory()).refineDataflow(entry.getValue());
            entry.setValue(refinedDataflow);
        }

        Statistics.addOrIncrementEntry("annotation_size", solutions.size());

        return new DefaultInferenceResult(solutions);
    }
}
