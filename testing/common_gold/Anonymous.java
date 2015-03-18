import checkers.inference.quals.VarAnnot;

// Test that parameters inside an anonymous class get annotated.
interface Interface {
    @VarAnnot(1)
    String get(@VarAnnot(3) String param);
}

class TestAnon {
    void context() {
        Object o = new Interface() {
            @Override
            public @VarAnnot(12) String get(@VarAnnot(14) String param) {
                return null;
            }
        };
    }
}
