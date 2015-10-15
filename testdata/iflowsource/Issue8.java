//@skip-test
public class Issue8{
   private void foo() {
      String s = "  ";
      for (int i = 1; i < 1; i++) {
            s += "|";
      }
   }
}
