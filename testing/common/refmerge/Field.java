class Field {

    static String f1 = "";
    static String f2 = f1;

    void test() {
        String two = f1;
    }
}
