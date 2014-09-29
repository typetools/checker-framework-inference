class TryCatch {

    void test() {
        Exception e = new Exception();

        String a = null;
        try {
            if (true) { throw e;}
            a = "";
        } catch (Exception e2) {
            a = "";
        }
        a.toString();

    }
}
