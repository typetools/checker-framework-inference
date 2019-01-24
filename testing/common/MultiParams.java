class MultiParams {
    void foo() {
        foo(new Object(), null);
    }

    void foo(Object a, String b) {
        a.toString();
    }
}
