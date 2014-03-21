import java.util.*;

public class MethodGenericsLocalCall {
	public <T extends Object> void genericMethodNoReturn(T t1) {
	}

	public void localCall() {
		this.<String>genericMethodNoReturn("test");
	}	
}

