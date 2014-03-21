class CallThis {
  Object o = null;
  void m1(Object o) {}

  Object m2() { return new Object(); }

  void m3(CallThis p) {
    this.m1(new Object());
    this.m1(this);
    p.m1(this);
    m1(this);
    Object x = this.m2();
    Object y = this;
  }
}
