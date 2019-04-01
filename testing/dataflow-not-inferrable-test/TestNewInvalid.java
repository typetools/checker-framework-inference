import dataflow.qual.DataFlow;
import java.util.ArrayList;

public class TestNewInvalid {
    // :: error: (assignment.type.incompatible) :: warning: (cast.unsafe.constructor.invocation)
    @DataFlow(typeNames={"java.util.List"}) ArrayList newTesing_invalid = new ArrayList();
}
