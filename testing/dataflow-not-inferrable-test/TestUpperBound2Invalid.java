import dataflow.qual.DataFlow;

public class TestUpperBound2Invalid {
    public @DataFlow(typeNames={"java.lang.Object"}) Object upperBoundTesting2_invalid(int c) {
        // :: error: (return.type.incompatible)
        return "I am a String!";
    }
}
