
import checkers.tainting.quals.*;
import java.util.List;
import java.util.ArrayList;

/**
 * General examples for inference.
 *
 * Qualifiers we expect to be inferred are in the
 * the qualifiers in comments, e.g.  /*@Untainted*\/
 */
class InferenceCheck {

    /**
     * Basic subtype relationship checks.
     */

    // Parameters
    void testParam(/*@Untainted*/ String in) {
        @Untainted String local = in;
    }

    // Transitive
    void testParamTransitive(/*@Untainted*/ String in) {
        /*@Untainted*/ String one = in;
        @Untainted String two = one;
    }

    // Method call
    void encryptedMethod(@Untainted String in) { }
    void testParamMethodCall(/*@Untainted*/ String in) {
        encryptedMethod(in);
    }

    // Cast
    void testCast(/*@Untainted*/ Object in) {
        @Untainted String local = (/*Encrypted*/ String) in;
    }

    // Local variables based on return type
    @Untainted String testReturn() {
        /*@Untainted*/ String res = null;
        return res;
    }

    // Inputs to a binary operation
    void testParamBinary(/*@Untainted*/ String in) {
        @Untainted String local = in + in;
    }

    // Inputs to a unary opartion
    void testParamUnary(/*@Untainted*/ Integer in) {
        @Untainted Integer local = ++in;
    }

    // Equality constraints between generic parameters
    void testEquality() {
        List<@Untainted String> local1 = new ArrayList</*@Untainted*/ String>();
        List</*@Tainted*/ String> local2 = new ArrayList<@Tainted String>();
    }

    // Wildcards
    void testWildcard() {
        List<? extends @Untainted String> local1 = new ArrayList</*@Untainted*/ String>();
        // Could infer @Untainted or @Tainted
        List<? super @Untainted String> local2 = new ArrayList</*@Untainted*/ String>();

        // Could infer @Untainted or @Tainted
        List<? extends @Tainted String> local3 = new ArrayList</*@Tainted*/ String>();
        List<? super @Tainted String> local4 = new ArrayList</*@Tainted*/ String>();

        List<? extends /*@Untainted*/ String> local5 = new ArrayList</*@Untainted*/ String>();
        // Force to be @Untainted
        @Untainted String localVal = local5.get(0);

        List<? super /*@Tainted*/ String> local6 = new ArrayList</*@Tainted*/ String>();
        // Force this be super @Tainted
        local6.add("");
    }
}

// Test that return types are inferred for overridden methods
class TestOverrides {
    @Untainted String testReturn () {
        return null;
    }

    class TestOverridesExt extends TestOverrides {
        @Override
        /*@Untainted*/ String testReturn() {
            return null;
        }
    }
}

// Test annotations on a receiver
class TestReceiver {
    void test (/*@Untainted*/ TestReceiver this) {
        @Untainted TestReceiver local = this;
    }
}

// Infer bounds annotations
class TestExtendsBound<T extends /*@Untainted*/ Object> {
    void testExtends(T t) {
        // T has to have an upper bound of @Untainted to satisfy this.
        @Untainted Object local = t;
    }
}

// T has a lower bound of @Tainted
class TestSuperBound</*@Tainted*/ T, @Tainted E> {
    void test() {
        new TestSuperBound<T, T>();
    }
}

// Infer annotations for qualifiers on type arguments.
class TestInvocation<T extends @Untainted Object> {
    void test() {
        new TestInvocation</*@Untainted*/ Object>();
    }
}
