import dataflow.qual.*;
import java.util.*;
public class TestByteCode {

	int a = 3;

	Object m() {
		if(a > 0) {
			// :: warning: (cast.unsafe.constructor.invocation)
            return new Object().toString();
		} else if (a < 0) {
            // :: warning: (cast.unsafe.constructor.invocation)
			return new ArrayList<String>();
		} else {
			return 3;
		}
	}
}
