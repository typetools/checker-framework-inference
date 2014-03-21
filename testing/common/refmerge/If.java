
class If {
    void test() {
        String a = null;  // 1
        if (true) {
            a = ""; // 2
        }
        a.toString(); // 3 (Merge variable)
    }
}
