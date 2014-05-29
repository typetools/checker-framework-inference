import ostrusted.quals.*;


class TestPolyNull {

    @PolyOsTrusted TestPolyNull(@PolyOsTrusted String astring) {

    }

    @OsTrusted TestPolyNull testConstructor1(String in) {
        return new TestPolyNull(in);
    }

    TestPolyNull testConstructor2(@OsUntrusted String in) {
        return new TestPolyNull(in);
    }

    @PolyOsTrusted String id(@PolyOsTrusted String in) {
        return in;
    }

    @OsTrusted String test(String s) {
        return id(s);
    }

    String test2(@OsUntrusted String s) {
        return id(s);
    }

}
