import checkers.inference.quals.VarAnnot;
class CallThis {
  @VarAnnot(2)
  Object o = null;
  void m1(@VarAnnot(4) Object o) {}

  @VarAnnot(5)
  Object m2() { return new Object(); }

  void m3(@VarAnnot(8) CallThis p) {
    this.m1(new Object());
    this.m1(this);
    p.m1(this);
    m1(this);
    Object x = this.m2();
    Object y = this;
  }
}
