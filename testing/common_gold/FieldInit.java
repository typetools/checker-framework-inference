import checkers.inference.quals.VarAnnot;
class FieldInit {
  @VarAnnot(5)
  Object f1 = ((@VarAnnot(3) Object) (null));
  @VarAnnot(6)
  Object f2 = new @VarAnnot(4) Object();
}
