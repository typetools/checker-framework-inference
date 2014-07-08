import checkers.inference.quals.VarAnnot;

class TestExpression {
    @VarAnnot(2)
    String s = (new @VarAnnot(4) String("a")) + (("" + "") + "b");
}
