
import java.io.Serializable;
import trusted.qual.*;

// This test is a good example of where an AnnotatedTypeTree shows up.
class TestIntersection {

    <T extends @Trusted Runnable & @Trusted Serializable> void test() {

    }
}
