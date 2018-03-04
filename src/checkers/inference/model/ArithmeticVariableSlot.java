package checkers.inference.model;

/**
 * ArithmeticVariableSlot represent the result of an arithmetic operation between two other
 * {@link VariableSlot}s. Note that this slot is serialized identically to a {@link VariableSlot}.
 */
public class ArithmeticVariableSlot extends VariableSlot {

    public ArithmeticVariableSlot(AnnotationLocation location, int id) {
        super(location, id);
    }

    @Override
    public Kind getKind() {
        return Kind.ARITHMETIC_VARIABLE;
    }

    @Override
    public <S, T> S serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    /**
     * ArithmeticVariables should never be re-inserted into the source code.
     *
     * @return false
     */
    @Override
    public boolean isInsertable() {
        return false;
    }
}
