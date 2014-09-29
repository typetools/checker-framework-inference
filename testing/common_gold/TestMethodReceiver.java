import checkers.inference.quals.VarAnnot;

class TestMethodReceiver {

    void test(@VarAnnot(2) TestMethodReceiver this, @VarAnnot(3) String s) {

    }

    static class Nested {
        void test(@VarAnnot(7) Nested this) {

        }
    }
}
