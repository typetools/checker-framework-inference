class IfParam {
    void test(String a) {
        if (true) {
            a = null;
        }
        a.toString();
    }
}
