import checkers.inference.quals.VarAnnot;
class CallLocal {
  void m1(@VarAnnot(3) Object o) {}

  @VarAnnot(4)
  Object m2() { return new Object(); }

  void m3(@VarAnnot(7) CallLocal p) {
    p.m1(new Object());
    Object x = p.m2();
  }
}
