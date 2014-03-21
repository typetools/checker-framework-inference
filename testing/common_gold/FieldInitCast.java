import checkers.inference.quals.VarAnnot;
class FieldInitCast {
  @VarAnnot(1)
  Object f;
  @VarAnnot(4)
  String g = (@VarAnnot(3) String) f;
}
