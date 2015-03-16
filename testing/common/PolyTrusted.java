import trusted.quals.*;


class TestPolyNull {

    @PolyTrusted TestPolyNull(@PolyTrusted String astring) {

    }

    @Trusted TestPolyNull testConstructor1(String in) {
        return new TestPolyNull(in);
    }

    TestPolyNull testConstructor2(@Untrusted String in) {
        return new TestPolyNull(in);
    }

    @PolyTrusted String id(@PolyTrusted String in) {
        return in;
    }

    @Trusted String test(String s) {
        return id(s);
    }

    String test2(@Untrusted String s) {
        return id(s);
    }

}
