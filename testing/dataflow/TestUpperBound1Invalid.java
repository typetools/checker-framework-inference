import dataflow.qual.DataFlow;

public class TestUpperBound1Invalid {
    public @DataFlow(typeNames={"float"}) int upperBoundTesting1_invalid(int c) {
        // :: error: (return.type.incompatible)
        return 3;
    }
}
