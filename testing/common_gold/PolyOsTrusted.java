import checkers.inference.quals.VarAnnot;
import ostrusted.qual.*;


class TestPolyNull {

    @PolyOsTrusted TestPolyNull(@PolyOsTrusted String astring) {

    }

    @OsTrusted TestPolyNull testConstructor1(@VarAnnot(3) String in) {
        return new TestPolyNull(in);
    }

    @VarAnnot(4)
    TestPolyNull testConstructor2(@OsUntrusted String in) {
        return new TestPolyNull(in);
    }

    @PolyOsTrusted String id(@PolyOsTrusted String in) {
        return in;
    }

    @OsTrusted String test(@VarAnnot(10) String s) {
        return id(s);
    }

    @VarAnnot(11)
    String test2(@OsUntrusted String s) {
        return id(s);
    }

}
