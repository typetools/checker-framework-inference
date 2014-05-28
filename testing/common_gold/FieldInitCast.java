import checkers.inference.quals.VarAnnot;
class FieldInitCast {
  @VarAnnot(2)
  Object f;
  @VarAnnot(3)
  String g = (@VarAnnot(4) String) f;
}
