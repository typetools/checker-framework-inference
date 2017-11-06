import dataflow.qual.DataFlow;

public class TestCharInvalid {
    // :: error: (assignment.type.incompatible)
    @DataFlow(typeNames={"int"}) char charTesting_invalid = 'L';
}
