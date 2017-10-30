//@skip-test
import sparta.checkers.qual.*;

abstract class StringConcatenateAssignment {
   abstract @Source("READ_SMS") String getSMS();
   String inferField = "";

   private void test() {
      String s = "";
            s += getSMS();
     // :: fixable-error: (assignment.type.incompatible)
     inferField = s;
   }
}

abstract class CompoundAssignment {
    abstract @Source("READ_SMS") int getSmsInt();
    abstract @Source("READ_TIME") int getTime();

    int inferFieldInt = 0;
    @Source("READ_SMS") int readSmsField;

    private void test() {
        int i = getTime();
        i += getSmsInt();
        // :: fixable-error: (assignment.type.incompatible)
        inferFieldInt = i;
        readSmsField = getTime();
    }
}
