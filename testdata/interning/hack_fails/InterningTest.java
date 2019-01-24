import java.util.*;

public class InterningTest<V> {
    void lubError(Map.Entry<Object, V> ent) { // Does NOT fail if V is replaced with Object.
        // Does NOT fail if these two lines are replaced with Object v = ent.getValue();
        Object v;
        v = ent.getValue();
    }
}
