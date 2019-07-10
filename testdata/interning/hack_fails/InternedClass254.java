// @skip-test The Interning Checker needs to be adapt to framework changes.
import interning.qual.Interned;

// :: error: (super.invocation.invalid)
@Interned class InternedClass {
    void foo(Object other) {
        boolean b = other instanceof InternedClass;
    }
}
