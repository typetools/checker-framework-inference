import checkers.inference.quals.VarAnnot;

class TestNestedClassName {

    static class Inner {

    }
    // Test that the method signature is generated correctly.
    void test(TestNestedClassName.@VarAnnot(3) Inner i) {
    }
}
