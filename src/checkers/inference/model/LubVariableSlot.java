package checkers.inference.model;

/**
 * LubVariableSlot models the least-upper-bounds of two other slots.
 */
public class LubVariableSlot extends VariableSlot {

    private final Slot left;
    private final Slot right;

    public LubVariableSlot(AnnotationLocation location, int id, Slot left, Slot right) {
        super(location, id);
        this.left = left;
        this.right = right;
    }

    @Override
    public Kind getKind() {
        return Kind.LUB_VARIABLE;
    }

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public Slot getLeft() {
        return left;
    }

    public Slot getRight() {
        return right;
    }

    /**
     * LubVariableSlot should never be inserted into the source code. record
     * does not correspond to an annotatable position.
     *
     * @return false
     */
    @Override
    public boolean isInsertable() {
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((left == null) ? 0 : left.hashCode());
        result = prime * result + ((right == null) ? 0 : right.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LubVariableSlot other = (LubVariableSlot) obj;
        if (left == null) {
            if (other.left != null)
                return false;
        } else if (!left.equals(other.left))
            return false;
        if (right == null) {
            if (other.right != null)
                return false;
        } else if (!right.equals(other.right))
            return false;
        return true;
    }
}
