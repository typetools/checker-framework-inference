import java.util.List;

class ForList {
    void test(List input) {
        Object out = null;
        for (Object o : input) {
            if (out == null) {
                out = o;
            }
            out.toString();
        }
    }

}
