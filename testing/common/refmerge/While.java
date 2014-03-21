class While {
    void test() {
        String a = "";
        while (System.currentTimeMillis() % 2 == 0) {
            a.toString();
            if (true) {
                a = null;
            } else {
                a = "";
            }
        }
    }
}
