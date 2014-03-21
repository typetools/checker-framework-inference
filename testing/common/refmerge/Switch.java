class Switch {
    void test() {
        int i = 1;
        String a = null;
        switch (i) {
            case 1:
                a = "";
                break;
            case 2:
                a = "";
                break;
            default:
                a = null;
                break;
        }
        a.toString();
    }
}
