//@skip-test
class Issue6  {
  void foo(Class<Enum<?>> c) {
  }
  void bar(Class<Enum<?>> c) {
     foo(c); // Must call a different method. A recursive call to bar does not reproduce the issue.
  }
}
