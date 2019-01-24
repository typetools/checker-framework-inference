public final class SequenceAndIndices<T extends Object> {
    public T seq;

    public boolean equals(SequenceAndIndices<T> other) {
        // Does NOT fail if this line is removed (replaced with "return true;")
        // :: fixable-error: (not.interned)
        return (this.seq == other.seq);
    }
}
