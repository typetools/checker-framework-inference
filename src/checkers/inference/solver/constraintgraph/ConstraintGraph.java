package checkers.inference.solver.constraintgraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.model.SubtypeConstraint;
import checkers.inference.model.VariableSlot;

/**
 * ConstraintGraph represents constraints in a graph form. Each constraint is an
 * edge, and each slot is a vertex. ConstraintGraph is used for separating
 * constraint into different components by running graph traversal algorithm on
 * it. Normal edges in this graph are bi-directional edges, except SubtypeEdge
 * is single-directed from subtype vertex to supertype vertex.
 * 
 * @author jianchu
 *
 */
public class ConstraintGraph {

    private final Set<Edge> edges;
    private final Set<Vertex> constantVerticies;
    private final Map<Vertex, Set<Constraint>> constantPath;
    private final Map<Integer, Vertex> verticies;
    private final List<Set<Constraint>> independentPath;

    protected ConstraintGraph() {
        this.edges = new HashSet<Edge>();
        this.constantVerticies = new HashSet<Vertex>();
        this.constantPath = new HashMap<Vertex, Set<Constraint>>();
        this.verticies = new HashMap<Integer, Vertex>();
        this.independentPath = new LinkedList<Set<Constraint>>();
    }

    protected void addEdge(Edge edge) {
        if (!this.edges.contains(edge)) {
            this.edges.add(edge);
        }
    }

    protected List<Vertex> getVerticies() {
        return new ArrayList<Vertex>(this.verticies.values());
    }

    protected Set<Edge> getEdges() {
        return Collections.unmodifiableSet(edges);
    }

    protected Set<Vertex> getConstantVerticies() {
        return Collections.unmodifiableSet(constantVerticies);
    }

    public Map<Vertex, Set<Constraint>> getConstantPath() {
        Map<Vertex, Set<Constraint>> tempMap = new HashMap<>();
        for (Map.Entry<Vertex, Set<Constraint>> entry : constantPath.entrySet()) {
            tempMap.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(tempMap);
    }

    protected void addConstantPath(Vertex vertex, Set<Constraint> constraints) {
        this.constantPath.put(vertex, constraints);
    }

    public List<Set<Constraint>> getIndependentPath() {
        List<Set<Constraint>> tempList = new ArrayList<>();
        for (Set<Constraint> path : independentPath) {
            tempList.add(Collections.unmodifiableSet(path));
        }
        return Collections.unmodifiableList(tempList);
    }

    protected void addIndependentPath(Set<Constraint> independentPath) {
        this.independentPath.add(independentPath);
    }

    protected void addConstant(Vertex vertex) {
        if (!this.constantVerticies.contains(vertex)) {
            this.constantVerticies.add(vertex);
        }
    }

    protected void createEdge(Slot slot1, Slot slot2, Constraint constraint) {
        Integer slot1Id = ((VariableSlot) slot1).getId();
        Integer slot2Id = ((VariableSlot) slot2).getId();
        Vertex vertex1;
        Vertex vertex2;

        if (this.verticies.keySet().contains(slot1Id)) {
            vertex1 = this.verticies.get(slot1Id);
        } else {
            vertex1 = new Vertex(slot1);
            this.verticies.put(slot1Id, vertex1);
        }

        if (this.verticies.keySet().contains(slot2Id)) {
            vertex2 = this.verticies.get(slot2Id);
        } else {
            vertex2 = new Vertex(slot2);
            this.verticies.put(slot2Id, vertex2);
        }

        Edge edge;

        if (constraint instanceof SubtypeConstraint) {
            edge = new SubtypeEdge(vertex1, vertex2, (SubtypeConstraint) constraint);
        } else {
            edge = new Edge(vertex1, vertex2, constraint);
        }

        this.addEdge(edge);
    }
}
