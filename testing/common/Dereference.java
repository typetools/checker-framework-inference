class Dereference {
  Dereference d;
  
  void foo() {
    Dereference d2 = d.d;
  }
}
