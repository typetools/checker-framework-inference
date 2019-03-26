package checkers.inference.model.tree;

import java.util.Objects;

import com.sun.source.tree.WildcardTree;
import com.sun.tools.javac.tree.JCTree.JCIdent;

/**
 * Artificial extend bound tree for representing extend bound of unbounded wildcard type.
 *
 * This class is used to represent the extend bound of an
 * unbounded wild card type. In order to let {@code VariableAnnotator}
 * be able to insert two different VarAnnotations on the super bound and the
 * extend bound of a unbounded wild card type.
 * (A VarAnnotation will be inserted with the location of this artificial extend bound tree,
 * and the VarAnnotation on wild card type would represent the "missing" bound --- super bound).
 *
 * Note: This class is specially created and should be only used by {@link VariableAnnotator}
 * as key of {@link VariableAnnotator#treeToVarAnnoPair} cache.
 *
 */
public class ArtificialExtendsBoundTree extends JCIdent {

    /**
     * The wild card tree bounded by this extend bound tree.
     */
    private final WildcardTree boundedWildcard;

    public ArtificialExtendsBoundTree(WildcardTree wildcardTree) {
        super(null, null);
        boundedWildcard = wildcardTree;
    }

    public WildcardTree getBoundedWildcard() {
        return boundedWildcard;
    }

    /**
     * The bounded Wildcard tree determine the equality of artificial extend bound trees.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ArtificialExtendsBoundTree) &&
                (this.boundedWildcard.equals(((ArtificialExtendsBoundTree) obj).getBoundedWildcard()));
    }

    @Override
    public int hashCode() {
        return Objects.hash("artificial", boundedWildcard);
    }
}
