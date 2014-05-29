import checkers.inference.quals.VarAnnot;

// Calls to ar.length used to fail because getAnnotatedType
// was never called on the length field.
class ArrayLength {
    @VarAnnot(3)
    String @VarAnnot(2) [] ar;
    @VarAnnot(4)
    int i = ar.length;
}
