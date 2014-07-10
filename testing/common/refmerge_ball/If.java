
class If {
    void test() {
        String a = null;  // 1
        if (a == null) { // 2 (then store)
            a = ""; // 3
        } // 4 (else store)
        a.toString(); // 5 (Merge variable)
    }
}
