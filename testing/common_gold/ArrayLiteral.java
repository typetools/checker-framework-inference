import checkers.inference.quals.VarAnnot;

class ArrayLiteral {
    @VarAnnot(3)
    String @VarAnnot(2) [] s = new @VarAnnot(5) String @VarAnnot(4) [] {};
}
