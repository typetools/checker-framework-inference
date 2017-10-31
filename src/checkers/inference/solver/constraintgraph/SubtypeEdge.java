package checkers.inference.solver.constraintgraph;

import checkers.inference.model.SubtypeConstraint;

/**
 * SubtypeEdge is for subtype constraint. It can be treated as directed edge.
 * 
 * @author jianchu
 *
 */
public class SubtypeEdge extends Edge {

    protected SubtypeEdge(Vertex from, Vertex to, SubtypeConstraint constraint) {
        super(from, to, constraint);
    }
}
