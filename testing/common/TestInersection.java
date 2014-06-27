
import java.io.Serializable;
import nninf.quals.*;

// This test is a good example of where an AnnotatedTypeTree shows up.
class TestIntersection {

    <T extends @NonNull Runnable & @NonNull Serializable> void test() {

    }
}
