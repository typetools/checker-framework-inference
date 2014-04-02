
class IfElse {
    void test() {
        String a = null;  // 1
        if (true) {
            a = ""; // 2
        } else {
            a = ""; // 3
        }
        a.toString(); // 4 (Merge variable)
    }
}
