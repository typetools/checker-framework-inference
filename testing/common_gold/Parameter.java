import checkers.inference.quals.VarAnnot;
class Parameter {
  void m(@VarAnnot(1) Object o) {
    o.toString();
  }
}
