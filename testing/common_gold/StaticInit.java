import checkers.inference.quals.VarAnnot;
class StaticInit {
  static @VarAnnot(2) Object f;

  static {
    f = new @VarAnnot(3) Object();
  }
}
