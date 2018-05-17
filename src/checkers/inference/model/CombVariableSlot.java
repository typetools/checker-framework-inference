package checkers.inference.model;

/**
 * CombVariableSlots represent locations whose values depend on two other VariableSlots.
 *
 * CombVariableSlots are used to model viewpoint adaptation result of two slots.
 *
 * TODO: Wener uses this class for Viewpoint adaptation.  All other locations are intended to be
 * TODO: used to represent LUBS.  Those uses should be replaced either with a new LubVariableSlot
 * TODO: or just a new VariableSlot with subtype constraints
 * TODO: One thing to note, is the viewpoint adaptation locations I believe should be
 * TODO: accompanied with a CombineConstraint where as the LUBs only use the subtype constraints
 */
public class CombVariableSlot extends VariableSlot {

    private final Slot first;
    private final Slot second;

    public CombVariableSlot(AnnotationLocation location, int id, Slot first, Slot second) {
        super(location, id);
        this.first = first;
        this.second = second;
    }

    @Override
    public Kind getKind() {
        return Kind.COMB_VARIABLE;
    }

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public Slot getFirst() {
        return first;
    }

    public Slot getSecond() {
        return second;
    }

    /**
     * CombVariables should never be re-inserted into the source code. record
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
        result = prime * result + ((first == null) ? 0 : first.hashCode());
        result = prime * result + ((second == null) ? 0 : second.hashCode());
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
        CombVariableSlot other = (CombVariableSlot) obj;
        if (first == null) {
            if (other.first != null)
                return false;
        } else if (!first.equals(other.first))
            return false;
        if (second == null) {
            if (other.second != null)
                return false;
        } else if (!second.equals(other.second))
            return false;
        return true;
    }
}
