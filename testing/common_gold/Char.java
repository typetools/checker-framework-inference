import checkers.inference.quals.VarAnnot;

// This must have caused an issue at some point
class TestChar {
    @VarAnnot(2)
    Object o = '1';
}
