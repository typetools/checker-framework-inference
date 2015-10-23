//@skip-test
import sparta.checkers.quals.*;
abstract class StringConcatenateAssignment{
   abstract @Source("READ_SMS") String getSMS();
   String inferField = "";
   private void test() {
      String s = "";
            s += getSMS();
     //:: fixable-error: (assignment.type.incompatible)
     inferField = s;
   }
}
