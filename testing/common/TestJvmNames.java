
import sparta.checkers.qual.*;

// Arrays and preannotated code can both cause issue with generating
// method signatures for JAIF insertion.
class TestJvmNames {
    void test(@Source() String[] one) { }
}
