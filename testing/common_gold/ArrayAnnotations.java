import checkers.inference.quals.VarAnnot;
class ArrayAnnotations {
    @VarAnnot(4)
    String @VarAnnot(1) [] @VarAnnot(2) [] @VarAnnot(3) [] s; 
}