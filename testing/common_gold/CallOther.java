import checkers.inference.quals.VarAnnot;
class Other {
  void m1(@VarAnnot(3) Object o) {}

  @VarAnnot(4)
  Object m2() { return new Object(); }

  void m3(@VarAnnot(7) Other p) {
    p.m1(new Object());
    Object x = p.m2();
  }
}

class CallOther {
  void foo(@VarAnnot(14) Other o) {
    o.m1(null);
    o.m2();
    o.m3(o);
  }
}
/* TODO: inheritance of fields
class TrickSuper {
  Other o;
}

class TrickSub extends TrickSuper {
  void m() {
    o.m1(null);
  }
}
*/
