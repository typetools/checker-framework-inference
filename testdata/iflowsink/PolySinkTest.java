import sparta.checkers.qual.*;

class PolySinkTest {

    void writeTime(@Sink("WRITE_TIME") int time) {
    }

    int absTime;

    void context(int toWrite) {
        // :: fixable-error: (assignment.type.incompatible)
        absTime = Math.abs(toWrite);
        writeTime(absTime);
    }
}
