
import checkers.inference.quals.VarAnnot;
import sparta.checkers.qual.*;

// Arrays and preannotated code can both cause issue with generating
// method signatures for JAIF insertion.
class TestJvmNames {
    void test(@Source() @VarAnnot(4) String @VarAnnot(3) [] one) { }
}
