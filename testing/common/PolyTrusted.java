import trusted.qual.*;


class TestPolyNull {

    @PolyTrusted TestPolyNull(@PolyTrusted String astring) {

    }

    @Trusted TestPolyNull testConstructor1(String in) {
        // TODO: This should certainly emit a warning as well if just typechecking
        return new TestPolyNull(in);
    }

    TestPolyNull testConstructor2(@Untrusted String in) {
        return new TestPolyNull(in);
    }

    @PolyTrusted String id(@PolyTrusted String in) {
        return in;
    }

    // TODO: This should certainly emit a warning as well if just typechecking
    @Trusted String test(String s) {
        return id(s);
    }

    String test2(@Untrusted String s) {
        return id(s);
    }

}
