import java.util.List;

class ForList {
    void test(List input) {
        Object out = null;
        for (Object o : input) {
            out = o;
            out.toString();
        }
        out.toString();
    }

}
