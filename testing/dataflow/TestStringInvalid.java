import dataflow.qual.DataFlow;

public class TestStringInvalid {
    // :: error: (assignment.type.incompatible)
    @DataFlow(typeNames={"java.lang.Object"}) String stingTesting_invalid = "I am a String!";
}
