import sparta.checkers.qual.*;

public class PolySourceTest {

    // @PolySource String poly( @PolySource String param) { return param; }
    int readSmsField;

    void foo(@Source("READ_SMS") int readSMS) {
        // :: fixable-error: (assignment.type.incompatible)
        readSmsField = Math.abs(readSMS);
        bar(Math.abs(readSMS));
        testReadSms(readSmsField);
    }

    void bar(int test) {
        // :: fixable-error: (argument.type.incompatible)
        testReadSms(test);
    }

    void testReadSms(@Source("READ_SMS") int sms) {}
}
