import checkers.inference.quals.VarAnnot;

// This used to fail because PLUS was used as the ASTPath instead of BINARY
class Addition {
    @VarAnnot(2)
    String s = "" + (@VarAnnot(5) String) null;
}
