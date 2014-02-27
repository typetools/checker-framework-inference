class TryCatchParam {

    void test(String a) {
        Exception e = new Exception();

        try {
            if (true) { throw e;}
            a = "";
        } catch (Exception e2) {
            a = "";
        }
        a.toString();

    }
}
