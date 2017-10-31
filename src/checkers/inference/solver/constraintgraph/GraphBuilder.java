package checkers.inference.solver.constraintgraph;

import org.checkerframework.javacutil.AnnotationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceMain;
import checkers.inference.model.AnnotationLocation.Kind;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.ExistentialConstraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import dataflow.DataflowVisitor;
import dataflow.util.DataflowUtils;

/**
 * GraphBuilder builds the constraint graph and runs graph traversal algorithms
 * to separate the graph in different components.
 * 
 * @author jianchu
 *
 */
public class GraphBuilder {
    private final Collection<Constraint> constraints;
    private final ConstraintGraph graph;
    private AnnotationMirror top;

    public GraphBuilder(Collection<Slot> slots, Collection<Constraint> constraints) {
        this.constraints = constraints;
        this.graph = new ConstraintGraph();
    }

    public GraphBuilder(Collection<Slot> slots, Collection<Constraint> constraints, AnnotationMirror top) {
        this(slots, constraints);
        this.top = top;
    }

    public ConstraintGraph buildGraph() {
        for (Constraint constraint : constraints) {
            if (constraint instanceof SubtypeConstraint) {
                addSubtypeEdge((SubtypeConstraint) constraint);
            } else if (constraint instanceof ExistentialConstraint) {
                continue;
            } else {
                ArrayList<Slot> slots = new ArrayList<Slot>();
                slots.addAll(constraint.getSlots());
                addEdges(slots, constraint);
            }
        }
        addConstant();
        calculateIndependentPath();
        calculateConstantPath();
        // printEdges();
        // printGraph();
        return getGraph();
    }

    /**
     * Find all constant vertices.
     */
    private void addConstant() {
        for (Vertex vertex : graph.getVerticies()) {
            if (vertex.isConstant()) {
                this.graph.addConstant(vertex);
            }
        }
    }

    /**
     * This method runs BFS based algorithm, and calculates all independent
     * components.
     */
    private void calculateIndependentPath() {
        Set<Vertex> visited = new HashSet<Vertex>();
        for (Vertex vertex : this.graph.getVerticies()) {
            if (!visited.contains(vertex)) {
                Set<Constraint> independentPath = new HashSet<Constraint>();
                Queue<Vertex> queue = new LinkedList<Vertex>();
                queue.add(vertex);
                while (!queue.isEmpty()) {
                    Vertex current = queue.remove();
                    visited.add(current);
                    for (Edge edge : current.getEdges()) {
                        independentPath.add(edge.getConstraint());
                        Vertex next = edge.getFromVertex().equals(current) ? edge.getToVertex() : edge
                                .getFromVertex();
                        if (!visited.contains(next)) {
                            queue.add(next);
                        }
                    }
                }
                this.graph.addIndependentPath(independentPath);
            }
        }
    }

    /**
     * For each constant vertex, this method run BFS based algorithm on it, and
     * and put all edges that can be reached by the vertex into one set.
     */
    private void calculateConstantPath() {
        for (Vertex vertex : this.graph.getConstantVerticies()) {
            Set<Constraint> constantPathConstraints = BFSSearch(vertex);
            this.graph.addConstantPath(vertex, constantPathConstraints);
        }
    }

    private Set<Constraint> BFSSearch(Vertex vertex) {
        Set<Constraint> constantPathConstraints = new HashSet<Constraint>();
        Queue<Vertex> queue = new LinkedList<Vertex>();
        queue.add(vertex);
        Set<Vertex> visited = new HashSet<Vertex>();
        while (!queue.isEmpty()) {
            Vertex current = queue.remove();
            visited.add(current);
            for (Edge edge : current.getEdges()) {
                if ((edge instanceof SubtypeEdge) && current.equals(edge.to)) {
                    continue;
                }
                Vertex next = current.equals(edge.getToVertex()) ? edge.getFromVertex() : edge.getToVertex();
                constantPathConstraints.add(edge.getConstraint());
                if (!visited.contains(next)) {
                    if (next.isConstant()) {
                        if (AnnotationUtils.areSame(top, next.getValue())) {
                            continue;
                        } else {
                            if (InferenceMain.getInstance().getVisitor() instanceof DataflowVisitor) {
                                String[] typeNames = DataflowUtils.getTypeNames(next.getValue());
                                if (typeNames.length == 1 && typeNames[0].length() == 0) {
                                    continue;
                                }
                            }
                        }
                    } else {
                        if (next.getSlot().getLocation() != null) {
                            if (next.getSlot().getLocation().getKind().equals(Kind.MISSING)) {
                                continue;
                            }
                        }
                    }
                    queue.add(next);
                }
            }
        }
        return constantPathConstraints;
    }

    private void addEdges(ArrayList<Slot> slots, Constraint constraint) {
        Slot first = slots.remove(0);
        for (int i = 0; i < slots.size(); i++) {
            Slot next = slots.get(i);
            if (first instanceof ConstantSlot && next instanceof ConstantSlot) {
                continue;
            }
            this.graph.createEdge(first, next, constraint);
            addEdges(slots, constraint);
        }
    }

    /**
     * The order of subtype and supertype matters, first one has to be subtype,
     * second one has to be supertype.
     * 
     * @param subtypeConstraint
     */
    private void addSubtypeEdge(SubtypeConstraint subtypeConstraint) {
        Slot subtype = subtypeConstraint.getSubtype();
        Slot supertype = subtypeConstraint.getSupertype();
        if (subtype instanceof ConstantSlot && supertype instanceof ConstantSlot) {
            return;
        }
        this.graph.createEdge(subtype, supertype, subtypeConstraint);
    }

    public ConstraintGraph getGraph() {
        return this.graph;
    }

    private void printEdges() {
        System.out.println("new graph!");
        for (Map.Entry<Vertex, Set<Constraint>> entry : this.graph.getConstantPath().entrySet()) {
            System.out.println(entry.getKey().getSlot());
            for (Constraint constraint : entry.getValue()) {
                System.out.println(constraint);
            }
            System.out.println("**************");
        }
    }

    private void printGraph() {
        for (Edge edge : this.graph.getEdges()) {
            System.out.println(edge);
        }

        for (Vertex vertex : this.graph.getVerticies()) {
            System.out.println(vertex.getId());
        }
    }
}
