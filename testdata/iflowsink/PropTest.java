import sparta.checkers.qual.*;

public class PropTest {
    String s;

    void otherTest() {
        s = "hello";
    }

    void test(String x) {
        send(s);
        // :: fixable-error: (argument.type.incompatible)
        send(x);
    }

    void send(@Sink("INTERNET") String s) {
    }

}
