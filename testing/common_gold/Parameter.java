import checkers.inference.quals.VarAnnot;
class Parameter {
  void m(@VarAnnot(3) Object o) {
    o.toString();
  }
}
