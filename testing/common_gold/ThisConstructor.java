import checkers.inference.quals.VarAnnot;
class CallThis {
    @VarAnnot(1)
    String s;
    @VarAnnot(2)
    Object o;

    CallThis(@VarAnnot(4) String s1) {
        this(s1, null);
    }

    CallThis(@VarAnnot(6) String in_s2, @VarAnnot(7) Object in_o2) {
        this.s = in_s2;
        this.o = in_o2;
    }
}
