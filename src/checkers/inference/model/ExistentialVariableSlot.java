package checkers.inference.model;

/**
 * Summary of Shorthand:
 *
 * <p>{@code (@0 | @1) - if (@0 exists) use @0 else use @1}
 *
 * <p>{@code (@2 (@0 | @1)} - the id of this existential variable is 2, {@code if (@0 exists) use @0
 * else use @1}
 *
 * <p>{@code (@3)} - This indicates that an annotation may or may not exist here and its id is
 * {@code @3}
 *
 * <p>IF we have a type parameter {@code <@4 T extends @5 Object>} then {@code (@3) T} is equivalent
 * to {@code <(@3 | @4) T extends (@3 | 5) Object>}
 *
 * <p>ExistentialVariableSlots represent variables that may or may not exist. These slots represent
 * parametric locations, locations where there is no annotation you could place that would result in
 * an equivalent meaning to omitting the variable.
 *
 * <p>Any non-local use of a type variable is a parametric. In these cases, the type variable will
 * be given an ExistentialVariableSlot
 *
 * <p>Often in comments, we abbreviate ExistentialVariable slots as either:
 *
 * <p>{@code (@0 | @1)} - indicating that if {@code @0} exists then use that otherwise use
 * {@code @1}
 *
 * <p>or
 *
 * <p>{@code (@5 (@0 | @1))} - indicating that if {@code @0} exists then use that otherwise use
 * {@code @1} and {@code @5} is the identifier for this Existential Variable slot.
 *
 * <p>Finally, if you see a variable alone in parentheses it means that variable may or may not
 * exist:
 *
 * <p>{@code (@2) T} - indicates {@code T} may have a primary annotation of {@code @2} If {@code
 * T}'s declaration were {@code <@0 T extends @1 Object>} then {@code (@2) T} corresponds to a type:
 * {@code <(@2 | @0) T extends (@2 | @1) Object>}
 *
 * <p>When "normalizing" constraints, we replace ExistentialVariableSlots by translating constraints
 * that contain them into Existential constraints.
 *
 * <p>That is, if we have a constraint:
 *
 * <p>{@code (@0 | @1) <: @3}
 *
 * <p>This really states:
 *
 * <p>if (@0 exists) {
 *
 * <p>@0 <: @3
 *
 * <p>} else {
 *
 * <p>@1 <: @3
 *
 * <p>}
 */
public class ExistentialVariableSlot extends VariableSlot {

    // a variable whose annotation may or may not exist in source code
    private final VariableSlot potentialSlot;

    // the variable which would take part in a constraint if potentialSlot does not exist
    private final VariableSlot alternativeSlot;

    public ExistentialVariableSlot(
            int id, VariableSlot potentialSlot, VariableSlot alternativeSlot) {
        super(id);
        setInsertable(false);

        if (potentialSlot == null) {
            throw new IllegalArgumentException(
                    "PotentialSlot cannot be null\n"
                            + "id="
                            + id
                            + "\n"
                            + "alternativeSlot="
                            + alternativeSlot);
        }

        if (alternativeSlot == null) {
            throw new IllegalArgumentException(
                    "alternativeSlot cannot be null\n"
                            + "id="
                            + id
                            + "\n"
                            + "potentialSlot="
                            + potentialSlot);
        }

        this.potentialSlot = potentialSlot;
        this.alternativeSlot = alternativeSlot;
    }

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public Kind getKind() {
        return Kind.EXISTENTIAL_VARIABLE;
    }

    public VariableSlot getPotentialSlot() {
        return potentialSlot;
    }

    public VariableSlot getAlternativeSlot() {
        return alternativeSlot;
    }

    @Override
    public int hashCode() {
        return 1129 * (potentialSlot.hashCode() + alternativeSlot.hashCode());
    }

    @Override
    public boolean equals(Object thatObj) {
        if (thatObj == this) return true;
        if (thatObj == null || !(thatObj instanceof ExistentialVariableSlot)) {
            return false;
        }

        final ExistentialVariableSlot that = (ExistentialVariableSlot) thatObj;
        return this.potentialSlot.equals(that.potentialSlot)
                && this.alternativeSlot.equals(that.alternativeSlot);
    }

    @Override
    public String toString() {
        return "ExistentialVariableSlot("
                + getId()
                + ", ("
                + potentialSlot.getId()
                + " | "
                + alternativeSlot.getId()
                + ")";
    }
}
