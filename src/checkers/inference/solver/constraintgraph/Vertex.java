package checkers.inference.solver.constraintgraph;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;

/**
 * Vertex represents a slot. Two vertices are same if they have same slot id.
 * 
 * @author jianchu
 *
 */
public class Vertex {

    private Set<Edge> edges;
    private Slot slot;
    private int id;
    private AnnotationMirror value;

    protected Vertex(Slot slot) {
        this.edges = new HashSet<Edge>();
        this.slot = slot;

        if (slot instanceof VariableSlot) {
            VariableSlot vs = (VariableSlot) slot;
            this.id = vs.getId();
            if (slot instanceof ConstantSlot) {
                ConstantSlot cs = (ConstantSlot) slot;
                this.value = cs.getValue();
            } else {
                this.value = null;
            }
        }
    }

    protected boolean isConstant() {
        return (this.slot instanceof ConstantSlot);
    }

    protected void addEdge(Edge edge) {
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
    }

    public Slot getSlot() {
        return this.slot;
    }

    protected int getId() {
        return this.id;
    }

    protected Set<Edge> getEdges() {
        return this.edges;
    }

    public AnnotationMirror getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Vertex) {
            Vertex vertex = (Vertex) o;
            if (vertex.id == this.id) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return id;
    }
}
