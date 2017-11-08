import dataflow.qual.DataFlow;

public class TestDoubleInvalid {
    // :: error: (assignment.type.incompatible)
    @DataFlow(typeNames={"int"}) double floatTesting_invalid = 3.14;
}
