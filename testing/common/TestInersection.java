
import java.io.Serializable;
import trusted.quals.*;

// This test is a good example of where an AnnotatedTypeTree shows up.
class TestIntersection {

    <T extends @Trusted Runnable & @Trusted Serializable> void test() {

    }
}
