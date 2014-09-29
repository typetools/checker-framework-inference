
import encrypted.quals.*;

class TestAddition {

    void test(<INFER: @Encrypted> String a, <INFER: @Encrypted> String b) {
        @Encrypted String t = a + b;
    }
}
