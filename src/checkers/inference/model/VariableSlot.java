package checkers.inference.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * VariableSlot is a Slot representing an undetermined value (i.e. a variable we are solving for).
 * After the Solver is run, each VariableSlot should have an assigned value which is then written
 * to the output Jaif file for later reinsertion into the original source code.
 *
 * Before the Solver is run, VariableSlots are represented by @VarAnnot( slot id ) annotations
 * on AnnotatedTypeMirrors.  When an AnnotatedTypeMirror is encountered in a position that would
 * generate constraints (e.g. either side of an assignment ), its @VarAnnots are converted into
 * VariableSlots which are then used in the generated constraints.
 *
 * E.g.  @VarAnnot(0) String s;
 * The above example implies that a VariableSlot with id 0 represents the possible annotations
 * on the declaration of s.
 *
 * Variable slot hold references to slots it is refined by, and slots it is merged to.
 *
 */
public class VariableSlot extends Slot {

    /**
     * Uniquely identifies this Slot.  id's are monotonically increasing in value by the order they
     * are generated
     */
    private final int id;

    /**
     * Should this VariableSlot be inserted back into the source code.
     * This should be false for types have have an implicit annotation
     * and slots for pre-annotated code.
     */
    private boolean insertable = true;

    /**
     * @param location Used to locate this variable in code, see @AnnotationLocation
     * @param id      Unique identifier for this variable
     */
    public VariableSlot(AnnotationLocation location, int id) {
        super(location);
        this.id = id;
    }

    // Slots this variable has been merged to.
    private final Set<LubVariableSlot> mergedToSlots = new HashSet<>();

    // Refinement variables that refine this slot.
    private final Set<RefinementVariableSlot> refinedToSlots = new HashSet<>();

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    public boolean isMergedTo(VariableSlot other) {
        for (VariableSlot mergedTo: mergedToSlots) {
            if (mergedTo.equals(other)) {
                return true;
            } else {
                if (mergedTo.isMergedTo(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getId() {
        return id;
    }

    public VariableSlot(int id) {
        this.id = id;
    }

    public Set<LubVariableSlot> getMergedToSlots() {
        return Collections.unmodifiableSet(mergedToSlots);
    }

    public void addMergedToSlot(LubVariableSlot mergedSlot) {
        this.mergedToSlots.add(mergedSlot);
    }

    public Set<RefinementVariableSlot> getRefinedToSlots() {
        return refinedToSlots;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + id + ")";
    }

    public boolean isInsertable() {
        return insertable;
    }

    public void setInsertable(boolean insertable) {
        this.insertable = insertable;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
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
        VariableSlot other = (VariableSlot) obj;
        if (id != other.id)
            return false;
        return true;
    }
}
