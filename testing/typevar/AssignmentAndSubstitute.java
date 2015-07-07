//Simple generic class to test type parameters
class AssignmentAndSubstitute<X extends String> {
    X x;
    X x2;

    static void strMethod(String str) {
    }

    void context() {
        strMethod(x);
        x2 = x;
    }

    static void substitution(String in_param) {
        AssignmentAndSubstitute<String> s = new AssignmentAndSubstitute<String>();
        s.x = in_param;
    }
}