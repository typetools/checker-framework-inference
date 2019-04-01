// @skip-test

import interning.qual.Interned;

// :: error: (super.invocation.invalid)
@Interned class InternedClass {
    void foo(Object other) {
        boolean b = other instanceof InternedClass;
    }
}
