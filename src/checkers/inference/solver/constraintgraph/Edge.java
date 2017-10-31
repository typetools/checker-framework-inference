package checkers.inference.solver.constraintgraph;

import java.util.Objects;

import checkers.inference.model.Constraint;

/**
 * Edge represents a constraint. Edge is undirected.
 * 
 * @author jianchu
 *
 */
public class Edge {

    protected Vertex from;
    protected Vertex to;
    protected Constraint constraint;

    protected Edge(Vertex from, Vertex to, Constraint constraint) {
        this.from = from;
        this.to = to;
        this.constraint = constraint;
        attachEdge();
    }

    protected void attachEdge() {
        from.addEdge(this);
        to.addEdge(this);
    }

    protected Vertex getFromVertex() {
        return this.from;
    }

    protected Vertex getToVertex() {
        return this.to;
    }

    protected Constraint getConstraint() {
        return this.constraint;
    }

    @Override
    public String toString() {
        return from.getId() + "--->" + to.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Edge)) {
            return false;
        }

        Edge edge = (Edge) o;

        return this.from.equals(edge.from) && this.to.equals(edge.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }
}
