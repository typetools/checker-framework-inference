
import interning.qual.Interned;
import java.util.Map;

public class MapAssignment {

    Map<String, @Interned String> a;
    Map<@Interned String, String> b;

    void maps() {
        // :: fixable-error: (assignment.type.incompatible)
        a = b;
    }
}
