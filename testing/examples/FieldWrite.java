class FieldWrite {
  Object f;

  void m() {
    f = new Object();
  }

  void n() {
    f = null;
  }
}

class FWOther {
  FieldWrite fw;

  void write1() {
    fw.f = new Object();
    this.fw.f = null;
  }

  void read1() {
    Object x;
    x = fw.f;
    x = fw.f;
  }

  void write2(FieldWrite p) {
    p.f = new Object();
    p.f = null;
  }
}
