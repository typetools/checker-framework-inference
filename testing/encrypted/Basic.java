import encrypted.quals.*;

public class Basic {

  void bar(@Plaintext String s, String c) {
      String b = s;

      concat(s, c, c);

      b = c;

      // flow refines b -> ok
      foo(b);
      TestInterface interf = new TestClass();
  }

  void concat(String s, String a, String b) {

      @Encrypted String safe = a + b;
      @Plaintext String ss = s + s;
  }

  String foo(@Encrypted String s2) {
    return s2;
  }

  interface TestInterface {
    public void myMethod(@Encrypted String s);
  }

  class TestClass implements TestInterface {
    public void myMethod(String s) {
      return;
    }
  }
}
