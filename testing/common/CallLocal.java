class CallLocal {
  void m1(Object o) {}

  Object m2() { return new Object(); }

  void m3(CallLocal p) {
    p.m1(new Object());
    Object x = p.m2();
  }
}
