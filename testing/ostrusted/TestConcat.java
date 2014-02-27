

class TestConcat {

    int field = -1;
    void test() {
        int size = 1;
        size += field;
    }

    String sfield = "";
    void test2() {
        String slocal = "";
        slocal += field;
    }
}
